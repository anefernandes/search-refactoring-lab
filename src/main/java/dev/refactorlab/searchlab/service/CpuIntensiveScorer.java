package dev.refactorlab.searchlab.service;

import dev.refactorlab.searchlab.model.ProductRecord;
import org.springframework.stereotype.Component;

@Component
public class CpuIntensiveScorer {

    public double score(ProductRecord product, double externalBoost, String query, int iterations) {
        // INTENTIONAL CPU-bound workload. Tune iterations through the API to profile throughput and latency.
        // The accumulated value is used in the score so the work cannot be trivially discarded by the JIT.
        double accumulator = 0.0;
        int seed = (int) (product.id() % 997);
        for (int i = 1; i <= iterations; i++) {
            double value = ((i + seed) % 1009) + 1;
            accumulator += Math.sqrt(value) * Math.sin((i + seed) * 0.001);
        }
        double textBoost = product.name().toLowerCase().contains(query.toLowerCase()) ? 1.25 : 0.0;
        double cpuSignal = iterations == 0 ? 0.0 : Math.abs(accumulator % 1.0) * 0.1;
        return product.baseScore() + externalBoost + textBoost + cpuSignal;
    }

    public double processWorkloadTask(int taskNumber, int iterations) {
        double accumulator = 0.0;
        int seed = Math.abs(taskNumber % 997);
        for (int i = 1; i <= iterations; i++) {
            accumulator += Math.sqrt(((i + seed) % 1013) + 1) * Math.cos((i + seed) * 0.001);
        }
        return accumulator;
    }
}
