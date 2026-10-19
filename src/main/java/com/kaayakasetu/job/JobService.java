package com.kaayakasetu.job;

import com.kaayakasetu.location.LocationService;
import com.kaayakasetu.location.ResolvedCity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Port of job_service.py. */
@Service
public class JobService {

    private final JobRepository repository;
    private final LocationService locationService;

    public JobService(JobRepository repository, LocationService locationService) {
        this.repository = repository;
        this.locationService = locationService;
    }

    /** Creates a job owned by the given employer account (null = no owner, only used for seed/demo data). */
    public Job createJob(JobCreateRequest j, Long ownerUserId) {
        double lat;
        double lon;
        if (j.latitude() == null || j.longitude() == null || (j.latitude() == 0 && j.longitude() == 0)) {
            ResolvedCity resolved = locationService.resolveCityCoords(j.city());
            lat = resolved.lat();
            lon = resolved.lon();
        } else {
            lat = j.latitude();
            lon = j.longitude();
        }
        String area = (j.area() == null || j.area().isEmpty()) ? j.city() : j.area();

        long id = repository.insert(j.title(), j.trade(), j.description(), j.employerName(), j.employerPhone(),
                j.city(), area, lat, lon, j.durationDays(), j.wageOffered(), j.paymentType(), j.urgency(),
                j.workersNeeded(), ownerUserId);
        return repository.findById(id).orElseThrow();
    }

    public Optional<Long> getOwnerUserId(long jobId) {
        return repository.findOwnerUserId(jobId);
    }

    public List<Job> listJobsOwnedBy(long ownerUserId) {
        return repository.findByOwner(ownerUserId);
    }

    public Optional<Job> getJob(long id) {
        return repository.findById(id);
    }

    public List<Job> listJobs(String trade, String city, String status, String urgency, Double minWage,
                              int limit, int offset) {
        return repository.search(trade, city, status, urgency, minWage, limit, offset);
    }

    public Optional<Job> updateJob(long id, JobUpdateRequest u) {
        Map<String, Object> cols = new LinkedHashMap<>();
        putIfPresent(cols, "title", u.title());
        putIfPresent(cols, "trade", u.trade());
        putIfPresent(cols, "description", u.description());
        putIfPresent(cols, "city", u.city());
        putIfPresent(cols, "area", u.area());
        putIfPresent(cols, "wage_offered", u.wageOffered());
        putIfPresent(cols, "urgency", u.urgency());
        putIfPresent(cols, "workers_needed", u.workersNeeded());
        putIfPresent(cols, "status", u.status());
        repository.update(id, cols);
        return repository.findById(id);
    }

    private static void putIfPresent(Map<String, Object> cols, String column, Object value) {
        if (value != null) {
            cols.put(column, value);
        }
    }
}
