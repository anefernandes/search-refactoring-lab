package dev.refactorlab.searchlab.model;

public record ProductRecord(
        long id,
        String name,
        String description,
        String category,
        double baseScore
) { }
