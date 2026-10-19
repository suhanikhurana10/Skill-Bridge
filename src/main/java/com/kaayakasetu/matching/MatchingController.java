package com.kaayakasetu.matching;

import com.kaayakasetu.auth.AuthUser;
import com.kaayakasetu.common.RequestChecks;
import com.kaayakasetu.job.JobService;
import com.kaayakasetu.worker.WorkerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/match")
@Tag(name = "Job Matching Microservice")
public class MatchingController {

    private final MatchingService matchingService;
    private final WorkerService workerService;
    private final JobService jobService;

    public MatchingController(MatchingService matchingService, WorkerService workerService, JobService jobService) {
        this.matchingService = matchingService;
        this.workerService = workerService;
        this.jobService = jobService;
    }

    /** Ranked open jobs for a worker (skill, distance, wage, reliability). */
    @GetMapping("/worker/{workerId}/jobs")
    public List<JobMatch> matchJobsForWorker(
            @PathVariable("workerId") long workerId,
            @RequestParam(name = "max_radius_km", defaultValue = "50.0") double maxRadiusKm,
            @RequestParam(name = "min_score", defaultValue = "40.0") double minScore,
            @RequestParam(name = "limit", defaultValue = "15") int limit,
            @AuthenticationPrincipal AuthUser user) {
        RequestChecks.range("max_radius_km", maxRadiusKm, 1.0, 200.0);
        RequestChecks.range("min_score", minScore, 0.0, 100.0);
        RequestChecks.range("limit", limit, 1, 50);
        if (workerService.getWorker(workerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker not found");
        }
        if (!user.isWorkerProfile(workerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only run matching for your own worker profile");
        }
        return matchingService.matchJobsForWorker(workerId, maxRadiusKm, minScore, limit);
    }

    /** Ranked candidate workers for a job, with an explanation per candidate. */
    @GetMapping("/job/{jobId}/workers")
    public List<WorkerMatch> matchWorkersForJob(
            @PathVariable("jobId") long jobId,
            @RequestParam(name = "max_radius_km", defaultValue = "50.0") double maxRadiusKm,
            @RequestParam(name = "min_score", defaultValue = "40.0") double minScore,
            @RequestParam(name = "limit", defaultValue = "15") int limit,
            @AuthenticationPrincipal AuthUser user) {
        RequestChecks.range("max_radius_km", maxRadiusKm, 1.0, 200.0);
        RequestChecks.range("min_score", minScore, 0.0, 100.0);
        RequestChecks.range("limit", limit, 1, 50);
        if (jobService.getJob(jobId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
        }
        if (!jobService.getOwnerUserId(jobId).map(owner -> user.isEmployer(owner)).orElse(false)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only find workers for jobs that you posted");
        }
        return matchingService.matchWorkersForJob(jobId, maxRadiusKm, minScore, limit);
    }
}
