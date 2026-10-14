package dev.refactorlab.searchlab.service;

import dev.refactorlab.searchlab.model.JobStatusResponse;
import dev.refactorlab.searchlab.model.JobSubmissionRequest;
import dev.refactorlab.searchlab.model.LabMetricsResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Service
public class WorkloadJobService {
    private final ThreadPoolExecutor executor;
    private final MockExternalProviderClient externalClient;
    private final CpuIntensiveScorer scorer;
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final LongAdder submittedTasks = new LongAdder();
    private final LongAdder succeededTasks = new LongAdder();
    private final LongAdder failedTasks = new LongAdder();
    private final LongAdder rejectedTasks = new LongAdder();

    public WorkloadJobService(ThreadPoolExecutor labExecutor,
                              MockExternalProviderClient externalClient,
                              CpuIntensiveScorer scorer) {
        this.executor = labExecutor;
        this.externalClient = externalClient;
        this.scorer = scorer;
    }

    public JobStatusResponse submit(JobSubmissionRequest request) {
        if (request.tenant().length() > 80) throw new IllegalArgumentException("tenant must be at most 80 characters");
        String jobId = UUID.randomUUID().toString();
        JobState state = new JobState(jobId, request.tenant(), request.tasks());
        jobs.put(jobId, state);
        submittedTasks.add(request.tasks());

        // INTENTIONAL: eagerly enqueue every task. Large jobs fill the shared queue ahead of later tenants.
        for (int task = 1; task <= request.tasks(); task++) {
            final int taskNumber = task;
            try {
                executor.execute(() -> {
                    state.started.incrementAndGet();
                    try {
                        externalClient.simulateWorkloadIo(request.tenant(), taskNumber, request.ioDelayMs());
                        if (request.failEvery() > 0 && taskNumber % request.failEvery() == 0) {
                            throw new IllegalStateException("Intentional failure at task " + taskNumber);
                        }
                        scorer.processWorkloadTask(taskNumber, request.cpuIterations());
                        state.succeeded.incrementAndGet();
                        succeededTasks.increment();
                    } catch (RuntimeException exception) {
                        state.failed.incrementAndGet();
                        failedTasks.increment();
                    } finally {
                        state.finishOne();
                    }
                });
            } catch (RejectedExecutionException exception) {
                state.rejected.incrementAndGet();
                rejectedTasks.increment();
                state.finishOne();
            }
        }
        return state.snapshot();
    }

    public JobStatusResponse get(String jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) throw new JobNotFoundException(jobId);
        return state.snapshot();
    }

    public LabMetricsResponse metrics() {
        return new LabMetricsResponse(
                executor.getPoolSize(), executor.getActiveCount(), executor.getCorePoolSize(),
                executor.getQueue().size(), executor.getQueue().remainingCapacity(),
                executor.getCompletedTaskCount(), submittedTasks.sum(), succeededTasks.sum(),
                failedTasks.sum(), rejectedTasks.sum(), jobs.size()
        );
    }

    private static final class JobState {
        private final String jobId;
        private final String tenant;
        private final int total;
        private final Instant submittedAt = Instant.now();
        private final long startNanos = System.nanoTime();
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger succeeded = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        private final AtomicInteger rejected = new AtomicInteger();
        private final AtomicInteger finished = new AtomicInteger();
        private volatile Instant completedAt;

        private JobState(String jobId, String tenant, int total) {
            this.jobId = jobId;
            this.tenant = tenant;
            this.total = total;
        }

        private void finishOne() {
            if (finished.incrementAndGet() >= total) completedAt = Instant.now();
        }

        private JobStatusResponse snapshot() {
            String status;
            int done = finished.get();
            if (done < total) status = started.get() == 0 ? "QUEUED" : "RUNNING";
            else if (failed.get() == 0 && rejected.get() == 0) status = "COMPLETED";
            else if (succeeded.get() > 0) status = "PARTIAL_FAILURE";
            else status = "FAILED";
            long elapsed = completedAt == null
                    ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                    : Duration.between(submittedAt, completedAt).toMillis();
            return new JobStatusResponse(jobId, tenant, status, total, started.get(), succeeded.get(),
                    failed.get(), rejected.get(), elapsed, submittedAt, completedAt);
        }
    }
}
