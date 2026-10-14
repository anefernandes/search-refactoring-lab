package dev.refactorlab.searchlab.model;

public record LabMetricsResponse(
        int poolSize,
        int activeThreads,
        int configuredThreads,
        int queueDepth,
        int queueRemainingCapacity,
        long completedExecutorTasks,
        long submittedTasks,
        long succeededTasks,
        long failedTasks,
        long rejectedTasks,
        int retainedJobs
) { }
