package dev.refactorlab.searchlab.service;

import dev.refactorlab.searchlab.model.ExternalSignal;
import dev.refactorlab.searchlab.model.ProductRecord;
import dev.refactorlab.searchlab.model.ProviderStatus;
import dev.refactorlab.searchlab.model.SearchResponse;
import dev.refactorlab.searchlab.model.SearchResult;
import dev.refactorlab.searchlab.repository.ProductSearchRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class SearchService {
    private static final List<String> PROVIDERS = List.of("catalog", "pricing", "reviews");
    private static final long PROVIDER_TIMEOUT_MS = 700;

    private final ProductSearchRepository repository;
    private final MockExternalProviderClient externalClient;
    private final CpuIntensiveScorer scorer;
    private final ThreadPoolExecutor executor;

    public SearchService(ProductSearchRepository repository,
                         MockExternalProviderClient externalClient,
                         CpuIntensiveScorer scorer,
                         ThreadPoolExecutor labExecutor) {
        this.repository = repository;
        this.externalClient = externalClient;
        this.scorer = scorer;
        this.executor = labExecutor;
    }

    public SearchResponse search(String query, String tenant, int limit, int cpuIterations, String failProvider) {
        long startedAt = System.nanoTime();
        if (query == null || query.trim().length() < 2 || query.length() > 120) {
            throw new IllegalArgumentException("q must contain between 2 and 120 characters");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        if (cpuIterations < 0 || cpuIterations > 250_000) {
            throw new IllegalArgumentException("cpuIterations must be between 0 and 250000");
        }
        if (tenant == null || tenant.isBlank() || tenant.length() > 80) {
            throw new IllegalArgumentException("tenant must contain between 1 and 80 characters");
        }
        if (failProvider != null && !failProvider.isBlank() && !PROVIDERS.contains(failProvider.toLowerCase())) {
            throw new IllegalArgumentException("failProvider must be catalog, pricing or reviews");
        }

        List<ProductRecord> products = repository.search(query.trim(), limit);
        List<ProviderStatus> providerStatuses = new ArrayList<>();
        Map<Long, Double> boosts = new HashMap<>();

        // We submit in parallel but await each future in list order. Under saturation this can
        // compound timeouts; use as a latency/refactoring exercise (e.g. allOf, deadlines, bulkheads).
        List<CompletableFuture<ProviderCallResult>> calls = new ArrayList<>();
        for (String provider : PROVIDERS) {
            CompletableFuture<ProviderCallResult> future = CompletableFuture.supplyAsync(() -> {
                long providerStarted = System.nanoTime();
                try {
                    ExternalSignal signal = externalClient.fetch(provider, query, products, failProvider);
                    return new ProviderCallResult(signal, null,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - providerStarted));
                } catch (RuntimeException error) {
                    return new ProviderCallResult(null, rootMessage(error),
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - providerStarted));
                }
            }, executor);
            calls.add(future);
        }

        boolean partial = false;
        for (int index = 0; index < calls.size(); index++) {
            String provider = PROVIDERS.get(index);
            long waitStarted = System.nanoTime();
            try {
                ProviderCallResult callResult = calls.get(index).get(PROVIDER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (callResult.signal() != null) {
                    callResult.signal().boosts().forEach((id, value) -> boosts.merge(id, value, Double::sum));
                    providerStatuses.add(new ProviderStatus(provider, "OK", callResult.elapsedMs(), "Mock response received"));
                } else {
                    partial = true;
                    providerStatuses.add(new ProviderStatus(provider, "FAILED", callResult.elapsedMs(), callResult.error()));
                }
            } catch (TimeoutException exception) {
                partial = true;
                calls.get(index).cancel(true);
                providerStatuses.add(new ProviderStatus(provider, "TIMEOUT",
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStarted), "Provider deadline exceeded"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                partial = true;
                providerStatuses.add(new ProviderStatus(provider, "INTERRUPTED", 0, "Search thread interrupted"));
            } catch (ExecutionException exception) {
                partial = true;
                providerStatuses.add(new ProviderStatus(provider, "FAILED", 0, rootMessage(exception)));
            }
        }

        List<CompletableFuture<SearchResult>> scoringTasks = new ArrayList<>();
        for (ProductRecord product : products) {
            double boost = boosts.getOrDefault(product.id(), 0.0);
            scoringTasks.add(CompletableFuture.supplyAsync(() -> {
                double score = scorer.score(product, boost, query, cpuIterations);
                return new SearchResult(product.id(), product.name(), product.category(), score,
                        "base score + matching text + mock provider signals + CPU workload signal");
            }, executor));
        }
        CompletableFuture.allOf(scoringTasks.toArray(new CompletableFuture[0])).join();
        List<SearchResult> results = scoringTasks.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed().thenComparing(SearchResult::id))
                .toList();

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return new SearchResponse(query.trim(), tenant, elapsedMs, partial, results.size(), results, providerStatuses);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    private record ProviderCallResult(ExternalSignal signal, String error, long elapsedMs) { }
}
