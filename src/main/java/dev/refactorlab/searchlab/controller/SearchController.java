package dev.refactorlab.searchlab.controller;

import dev.refactorlab.searchlab.model.SearchResponse;
import dev.refactorlab.searchlab.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(
            @RequestParam("q") String query,
            @RequestParam(name = "tenant", defaultValue = "public") String tenant,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cpuIterations", defaultValue = "10000") int cpuIterations,
            @RequestParam(name = "failProvider", required = false) String failProvider) {
        return searchService.search(query, tenant, limit, cpuIterations, failProvider);
    }
}
