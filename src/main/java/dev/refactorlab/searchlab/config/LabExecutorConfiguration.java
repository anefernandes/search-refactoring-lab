package dev.refactorlab.searchlab.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class LabExecutorConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor labExecutor(@Value("${searchlab.executor.threads:8}") int threads) {
        AtomicInteger sequence = new AtomicInteger();
        // INTENTIONAL TECHNICAL DEBT for the exercise:
        // - unbounded FIFO queue can grow under sustained load;
        // - blocking I/O and CPU work compete for the same threads;
        // - all tenants share one queue, so a large tenant batch can dominate it.
        return new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "lab-worker-" + sequence.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                }
        );
    }
}
