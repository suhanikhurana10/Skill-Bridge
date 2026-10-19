package com.kaayakasetu.job;

import com.kaayakasetu.auth.AuthUser;
import com.kaayakasetu.common.RequestChecks;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs Microservice")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /** Employers post work vacancies. The job is owned by the logged-in employer. */
    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Job post(@Valid @RequestBody JobCreateRequest job, @AuthenticationPrincipal AuthUser user) {
        return jobService.createJob(job, user.id());
    }

    /** The jobs posted by the logged-in employer (any status). */
    @GetMapping("/mine")
    public List<Job> mine(@AuthenticationPrincipal AuthUser user) {
        return jobService.listJobsOwnedBy(user.id());
    }

    /** List and filter job opportunities (default: only open ones). */
    @GetMapping({"", "/"})
    public List<Job> list(
            @RequestParam(name = "trade", required = false) String trade,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "status", defaultValue = "open") String status,
            @RequestParam(name = "urgency", required = false) String urgency,
            @RequestParam(name = "min_wage", required = false) Double minWage,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        RequestChecks.range("limit", limit, 1, 100);
        RequestChecks.atLeast("offset", offset, 0);
        return jobService.listJobs(trade, city, status, urgency, minWage, limit, offset);
    }

    @GetMapping("/{jobId}")
    public Job get(@PathVariable("jobId") long jobId) {
        return jobService.getJob(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found"));
    }

    /** Update job parameters or status (e.g. completed / filled). Only the employer who posted the job may do this. */
    @PatchMapping("/{jobId}")
    public Job update(@PathVariable("jobId") long jobId, @RequestBody JobUpdateRequest updates,
                      @AuthenticationPrincipal AuthUser user) {
        if (jobService.getJob(jobId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found");
        }
        if (!jobService.getOwnerUserId(jobId).map(owner -> user.isEmployer(owner)).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit jobs that you posted");
        }
        return jobService.updateJob(jobId, updates)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job posting not found"));
    }
}
