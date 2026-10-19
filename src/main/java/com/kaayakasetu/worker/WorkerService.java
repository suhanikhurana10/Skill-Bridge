package com.kaayakasetu.worker;

import com.kaayakasetu.common.PyMath;
import com.kaayakasetu.location.LocationService;
import com.kaayakasetu.location.ResolvedCity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Port of worker_service.py. */
@Service
public class WorkerService {

    private final WorkerRepository repository;
    private final LocationService locationService;

    public WorkerService(WorkerRepository repository, LocationService locationService) {
        this.repository = repository;
        this.locationService = locationService;
    }

    public Worker createWorker(WorkerCreateRequest w) {
        double lat;
        double lon;
        // No coordinates (or 0,0) -> look them up from the city name
        if (w.latitude() == null || w.longitude() == null || (w.latitude() == 0 && w.longitude() == 0)) {
            ResolvedCity resolved = locationService.resolveCityCoords(w.city());
            lat = resolved.lat();
            lon = resolved.lon();
        } else {
            lat = w.latitude();
            lon = w.longitude();
        }

        double hourly = w.hourlyRate() != null
                ? w.hourlyRate()
                : PyMath.round(w.dailyRate() / 8.0, 1);

        // Python: `worker.area or worker.city` (empty string counts as "not given")
        String area = (w.area() == null || w.area().isEmpty()) ? w.city() : w.area();

        long id = repository.insert(w.name(), w.phone(), w.trade(), w.secondarySkills(), w.experienceYears(),
                w.dailyRate(), hourly, w.city(), area, lat, lon, w.availability(), w.languagePref(), w.source());
        return repository.findById(id).orElseThrow();
    }

    public Optional<Worker> getWorker(long id) {
        return repository.findById(id);
    }

    public Optional<Worker> getWorkerByPhone(String phone) {
        return repository.findByPhone(phone);
    }

    public List<Worker> listWorkers(String trade, String city, String availability, Double maxDailyRate,
                                    Integer minExperience, int limit, int offset) {
        return repository.search(trade, city, availability, maxDailyRate, minExperience, limit, offset);
    }

    /** Applies only the fields that were sent (non-null). Returns empty if the worker does not exist. */
    public Optional<Worker> updateWorker(long id, WorkerUpdateRequest u) {
        Map<String, Object> cols = new LinkedHashMap<>();
        putIfPresent(cols, "name", u.name());
        putIfPresent(cols, "trade", u.trade());
        putIfPresent(cols, "secondary_skills", u.secondarySkills());
        putIfPresent(cols, "experience_years", u.experienceYears());
        putIfPresent(cols, "daily_rate", u.dailyRate());
        putIfPresent(cols, "hourly_rate", u.hourlyRate());
        putIfPresent(cols, "city", u.city());
        putIfPresent(cols, "area", u.area());
        putIfPresent(cols, "latitude", u.latitude());
        putIfPresent(cols, "longitude", u.longitude());
        putIfPresent(cols, "availability", u.availability());
        putIfPresent(cols, "language_pref", u.languagePref());
        repository.update(id, cols);
        return repository.findById(id);
    }

    private static void putIfPresent(Map<String, Object> cols, String column, Object value) {
        if (value != null) {
            cols.put(column, value);
        }
    }
}
