package dev.refactorlab.searchlab.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record JobSubmissionRequest(
        @NotBlank String tenant,
        @Min(1) @Max(5000) int tasks,
        @Min(0) @Max(2000) int ioDelayMs,
        @Min(0) @Max(250000) int cpuIterations,
        @Min(0) @Max(5000) int failEvery
) { }
