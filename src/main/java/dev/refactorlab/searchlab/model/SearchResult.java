package dev.refactorlab.searchlab.model;

public record SearchResult(
        long id,
        String name,
        String category,
        double score,
        String explanation
) { }
