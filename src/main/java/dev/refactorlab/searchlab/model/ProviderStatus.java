package dev.refactorlab.searchlab.model;

public record ProviderStatus(
        String provider,
        String status,
        long elapsedMs,
        String detail
) { }
