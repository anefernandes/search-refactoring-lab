package dev.refactorlab.searchlab.model;

import java.util.Map;

public record ExternalSignal(String provider, Map<Long, Double> boosts) { }
