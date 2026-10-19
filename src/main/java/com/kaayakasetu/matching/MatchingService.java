package com.kaayakasetu.matching;

import com.kaayakasetu.common.Fmt;
import com.kaayakasetu.common.PyMath;
import com.kaayakasetu.job.Job;
import com.kaayakasetu.job.JobService;
import com.kaayakasetu.location.LocationService;
import com.kaayakasetu.worker.Worker;
import com.kaayakasetu.worker.WorkerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Port of matching_service.py - a transparent, rule-based scoring engine (not machine learning).
 *
 *   total = 40% skill + 30% distance + 20% wage + 10% reliability   (each part is 0..1, result x 100)
 *
 * Every result carries a plain-language explanation so a worker/employer can see WHY it matched.
 */
@Service
public class MatchingService {

    private static final Map<String, List<String>> RELATED_TRADES = Map.of(
            "electrician", List.of("ac technician", "appliance tech", "inverter repair", "helper"),
            "plumber", List.of("tile mason", "sanitary worker", "helper"),
            "mason", List.of("tile mason", "plasterer", "concrete worker", "helper"),
            "carpenter", List.of("wood polish", "furniture fitter", "helper"),
            "painter", List.of("waterproofing", "putty worker", "helper"),
            "welder", List.of("fabricator", "blacksmith", "helper"),
            "tile_mason", List.of("mason", "flooring", "helper"),
            "helper", List.of("loader", "gardener", "construction worker"));

    private final LocationService locationService;
    private final WorkerService workerService;
    private final JobService jobService;

    public MatchingService(LocationService locationService, WorkerService workerService, JobService jobService) {
        this.locationService = locationService;
        this.workerService = workerService;
        this.jobService = jobService;
    }

    // ------------------------------------------------------------------ scoring pieces

    public double calculateSkillScore(String workerTrade, String workerSkills, String jobTrade) {
        String w = blank(workerTrade);
        String j = blank(jobTrade);
        String skills = blank(workerSkills);

        if (w.equals(j)) {
            return 1.0;                                   // exact trade
        }
        if (skills.contains(j)) {
            return 0.85;                                  // listed as a secondary skill
        }
        for (String related : RELATED_TRADES.getOrDefault(w, List.of())) {
            if (j.contains(related)) {
                return 0.70;                              // related trade
            }
        }
        if (w.equals("helper") || j.equals("helper")) {
            return 0.50;                                  // helper fallback
        }
        return 0.15;
    }

    public double calculateDistanceScore(double distKm, double maxRadius) {
        if (distKm <= 5.0) return 1.0;
        if (distKm <= 15.0) return 0.85;
        if (distKm <= 30.0) return 0.65;
        if (distKm <= maxRadius) return 0.40;
        return 0.10;
    }

    public double calculateWageScore(double workerRate, double jobOffer) {
        if (workerRate <= 0) return 1.0;
        double ratio = jobOffer / workerRate;
        if (ratio >= 1.0) return 1.0;     // employer pays at least what the worker asks
        if (ratio >= 0.85) return 0.80;   // within a 15% negotiation range
        if (ratio >= 0.70) return 0.50;
        return 0.20;
    }

    /** Composite score between one worker and one job. */
    public MatchScoreBreakdown scoreMatch(Worker worker, Job job, double maxRadiusKm) {
        double distKm = locationService.haversineDistance(
                worker.latitude(), worker.longitude(), job.latitude(), job.longitude());

        double skillScore = calculateSkillScore(worker.trade(), worker.secondarySkills(), job.trade());
        double distanceScore = calculateDistanceScore(distKm, maxRadiusKm);
        double wageScore = calculateWageScore(worker.dailyRate(), job.wageOffered());

        // Reliability = 60% star rating + 40% availability
        double ratingScore = Math.min(worker.rating() / 5.0, 1.0);
        String availability = worker.availability();
        double availScore = "available".equals(availability) ? 1.0 : ("on_call".equals(availability) ? 0.7 : 0.3);
        double reliabilityScore = (ratingScore * 0.6) + (availScore * 0.4);

        double composite = ((skillScore * 0.40) + (distanceScore * 0.30) + (wageScore * 0.20)
                + (reliabilityScore * 0.10)) * 100.0;
        double totalScore = PyMath.round(composite, 1);

        List<String> parts = new ArrayList<>();
        if (skillScore >= 0.85) {
            parts.add("Strong skill match (" + job.trade() + ")");
        } else {
            parts.add("Related trade (" + worker.trade() + " -> " + job.trade() + ")");
        }
        String place = (job.area() == null || job.area().isEmpty()) ? job.city() : job.area();
        parts.add(distKm + " km away (" + place + ")");
        if (wageScore >= 1.0) {
            parts.add("Fair pay (₹" + Fmt.f0(job.wageOffered()) + " >= ₹" + Fmt.f0(worker.dailyRate()) + " requested)");
        } else {
            parts.add("Offer ₹" + Fmt.f0(job.wageOffered()) + " vs requested ₹" + Fmt.f0(worker.dailyRate()));
        }

        return new MatchScoreBreakdown(
                PyMath.round(skillScore * 100, 1),
                PyMath.round(distanceScore * 100, 1),
                PyMath.round(wageScore * 100, 1),
                PyMath.round(reliabilityScore * 100, 1),
                PyMath.round(availScore * 100, 1),
                totalScore,
                distKm,
                String.join("; ", parts));
    }

    // ------------------------------------------------------------------ ranking

    /** Best open jobs for one worker. Returns an empty list if the worker does not exist. */
    public List<JobMatch> matchJobsForWorker(long workerId, double maxRadiusKm, double minScore, int limit) {
        Optional<Worker> found = workerService.getWorker(workerId);
        if (found.isEmpty()) {
            return List.of();
        }
        Worker worker = found.get();

        List<JobMatch> matched = new ArrayList<>();
        for (Job job : jobService.listJobs(null, null, "open", null, null, 200, 0)) {
            MatchScoreBreakdown s = scoreMatch(worker, job, maxRadiusKm);
            if (s.distanceKm() <= maxRadiusKm && s.totalScore() >= minScore) {
                matched.add(new JobMatch(job, s.totalScore(), s));
            }
        }
        // List.sort is stable, like Python's sort: equal scores keep their original order
        matched.sort(Comparator.comparingDouble(JobMatch::matchScore).reversed());
        return matched.subList(0, Math.min(limit, matched.size()));
    }

    /** Best workers for one job. Returns an empty list if the job does not exist. */
    public List<WorkerMatch> matchWorkersForJob(long jobId, double maxRadiusKm, double minScore, int limit) {
        Optional<Job> found = jobService.getJob(jobId);
        if (found.isEmpty()) {
            return List.of();
        }
        Job job = found.get();

        List<WorkerMatch> matched = new ArrayList<>();
        for (Worker worker : workerService.listWorkers(null, null, null, null, null, 200, 0)) {
            MatchScoreBreakdown s = scoreMatch(worker, job, maxRadiusKm);
            if (s.distanceKm() <= maxRadiusKm && s.totalScore() >= minScore) {
                matched.add(new WorkerMatch(worker, s.totalScore(), s));
            }
        }
        matched.sort(Comparator.comparingDouble(WorkerMatch::matchScore).reversed());
        return matched.subList(0, Math.min(limit, matched.size()));
    }

    private static String blank(String s) {
        return s == null ? "" : s.strip().toLowerCase();
    }
}
