package dev.refactorlab.searchlab.service;

import dev.refactorlab.searchlab.model.ExternalSignal;
import dev.refactorlab.searchlab.model.ProductRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MockExternalProviderClient {
    private final int catalogDelayMs;
    private final int pricingDelayMs;
    private final int reviewsDelayMs;

    public MockExternalProviderClient(
            @Value("${searchlab.external-providers.catalog-delay-ms:70}") int catalogDelayMs,
            @Value("${searchlab.external-providers.pricing-delay-ms:130}") int pricingDelayMs,
            @Value("${searchlab.external-providers.reviews-delay-ms:190}") int reviewsDelayMs) {
        this.catalogDelayMs = catalogDelayMs;
        this.pricingDelayMs = pricingDelayMs;
        this.reviewsDelayMs = reviewsDelayMs;
    }

    public ExternalSignal fetch(String provider, String query, List<ProductRecord> products, String failProvider) {
        int delay = switch (provider) {
            case "catalog" -> catalogDelayMs;
            case "pricing" -> pricingDelayMs;
            case "reviews" -> reviewsDelayMs;
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
        sleep(delay);
        if (provider.equalsIgnoreCase(failProvider == null ? "" : failProvider)) {
            throw new ExternalProviderException(provider + " mock provider failed intentionally");
        }

        Map<Long, Double> boosts = new HashMap<>();
        int providerSeed = Math.abs(provider.hashCode() % 7) + 1;
        for (ProductRecord product : products) {
            // Deterministic fake partner signal: repeatable in tests and during prompt experiments.
            if ((product.id() + providerSeed) % (providerSeed % 3 + 2) == 0) {
                boosts.put(product.id(), providerSeed * 0.15);
            }
        }
        return new ExternalSignal(provider, boosts);
    }

    public void simulateWorkloadIo(String tenant, int taskNumber, int delayMs) {
        sleep(delayMs);
        // Deterministic simulated I/O result; no network call is made.
        if (tenant == null || tenant.isBlank() || taskNumber < 0) {
            throw new IllegalArgumentException("Invalid workload task context");
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalProviderException("Mock I/O interrupted", exception);
        }
    }
}
