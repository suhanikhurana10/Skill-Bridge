package com.kaayakasetu.worker;

import com.kaayakasetu.auth.AuthUser;
import com.kaayakasetu.auth.UserRepository;
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

/**
 * Workers module. NOTE the {"", "/"} mappings: FastAPI accepted both /api/v1/workers and /api/v1/workers/,
 * and the frontend calls the version WITH the trailing slash. Spring Boot 3 treats them as different URLs,
 * so we map both.
 */
@RestController
@RequestMapping("/api/v1/workers")
@Tag(name = "Workers Microservice")
public class WorkerController {

    private final WorkerService workerService;
    private final UserRepository userRepository;

    public WorkerController(WorkerService workerService, UserRepository userRepository) {
        this.workerService = workerService;
        this.userRepository = userRepository;
    }

    /**
     * Create the worker profile of the logged-in WORKER account (one profile per account,
     * and its phone number must be the account's phone number).
     */
    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Worker register(@Valid @RequestBody WorkerCreateRequest worker, @AuthenticationPrincipal AuthUser user) {
        if (user.workerId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a worker profile");
        }
        if (!worker.phone().strip().equals(user.phone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The profile phone number must be the phone number of your account");
        }
        if (workerService.getWorkerByPhone(worker.phone()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Worker with this phone number already registered");
        }
        Worker created = workerService.createWorker(worker);
        userRepository.linkWorker(user.id(), created.id());
        return created;
    }

    /** Search and list worker profiles with flexible filters. */
    @GetMapping({"", "/"})
    public List<Worker> list(
            @RequestParam(name = "trade", required = false) String trade,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "availability", required = false) String availability,
            @RequestParam(name = "max_daily_rate", required = false) Double maxDailyRate,
            @RequestParam(name = "min_experience", required = false) Integer minExperience,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        RequestChecks.range("limit", limit, 1, 100);
        RequestChecks.atLeast("offset", offset, 0);
        return workerService.listWorkers(trade, city, availability, maxDailyRate, minExperience, limit, offset);
    }

    @GetMapping("/{workerId}")
    public Worker get(@PathVariable("workerId") long workerId) {
        return workerService.getWorker(workerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker profile not found"));
    }

    /** Update availability, rates, trade, skills ... (only the fields you send). Only the profile's owner may do this. */
    @PatchMapping("/{workerId}")
    public Worker update(@PathVariable("workerId") long workerId, @RequestBody WorkerUpdateRequest updates,
                         @AuthenticationPrincipal AuthUser user) {
        if (workerService.getWorker(workerId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker profile not found");
        }
        if (!user.isWorkerProfile(workerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only edit your own worker profile");
        }
        return workerService.updateWorker(workerId, updates)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Worker profile not found"));
    }
}
