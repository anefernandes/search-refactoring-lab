package dev.refactorlab.searchlab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchesSeededDatabaseAndReturnsProviderStatuses() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("java"))
                .andExpect(jsonPath("$.resultCount", greaterThan(0)))
                .andExpect(jsonPath("$.providers.length()").value(3));
    }

    @Test
    void providerFailureReturnsPartialResultsInsteadOfFailingWholeSearch() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "java").param("failProvider", "pricing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partialResults").value(true))
                .andExpect(jsonPath("$.providers[1].status").value("FAILED"));
    }

    @Test
    void rejectsTooShortQueries() throws Exception {
        mockMvc.perform(get("/api/search").param("q", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
    @Test
    void acceptsAsyncWorkloadJob() throws Exception {
        mockMvc.perform(post("/api/lab/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenant\":\"test-team\",\"tasks\":5,\"ioDelayMs\":0,\"cpuIterations\":0,\"failEvery\":0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.totalTasks").value(5));
    }

    @Test
    void exposesExecutorMetrics() throws Exception {
        mockMvc.perform(get("/api/lab/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolSize").exists())
                .andExpect(jsonPath("$.queueDepth").exists());
    }

}
