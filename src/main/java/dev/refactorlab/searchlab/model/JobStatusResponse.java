package dev.refactorlab.searchlab.model;

import java.time.Instant;

public record JobStatusResponse(
        String jobId,
        String tenant,
        String status,
        int totalTasks,
        int startedTasks,
        int succeededTasks,
        int failedTasks,
        int rejectedTasks,
        long elapsedMs,
        Instant submittedAt,
        Instant completedAt
) { }
