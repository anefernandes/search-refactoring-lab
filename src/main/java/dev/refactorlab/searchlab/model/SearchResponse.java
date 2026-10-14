package dev.refactorlab.searchlab.model;

import java.util.List;

public record SearchResponse(
        String query,
        String tenant,
        long elapsedMs,
        boolean partialResults,
        int resultCount,
        List<SearchResult> results,
        List<ProviderStatus> providers
) { }
