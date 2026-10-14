package dev.refactorlab.searchlab.controller;

import dev.refactorlab.searchlab.model.JobStatusResponse;
import dev.refactorlab.searchlab.model.JobSubmissionRequest;
import dev.refactorlab.searchlab.model.LabMetricsResponse;
import dev.refactorlab.searchlab.service.WorkloadJobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab")
public class LabController {
    private final WorkloadJobService jobService;

    public LabController(WorkloadJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobStatusResponse submit(@Valid @RequestBody JobSubmissionRequest request) {
        return jobService.submit(request);
    }

    @GetMapping("/jobs/{jobId}")
    public JobStatusResponse get(@PathVariable String jobId) {
        return jobService.get(jobId);
    }

    @GetMapping("/metrics")
    public LabMetricsResponse metrics() {
        return jobService.metrics();
    }
}
