package com.kaayakasetu.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaayakasetu.common.PyMath;
import com.kaayakasetu.job.Job;
import com.kaayakasetu.job.JobService;
import com.kaayakasetu.location.LocationService;
import com.kaayakasetu.location.ResolvedCity;
import com.kaayakasetu.worker.Worker;
import com.kaayakasetu.worker.WorkerService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Port of feed_service.py: personalised job/worker feeds and the marketplace insights. */
@Service
public class FeedService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final LocationService locationService;
    private final JobService jobService;
    private final WorkerService workerService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public FeedService(LocationService locationService, JobService jobService, WorkerService workerService,
                       JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.locationService = locationService;
        this.jobService = jobService;
        this.workerService = workerService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** An entity together with its distance from the viewer (null when we do not know where the viewer is). */
    private record WithDistance<T>(T item, Double distanceKm) {}

    /** Open jobs for a worker. Urgent first, then nearest (if we know where the worker is) or newest. */
    public List<Map<String, Object>> getJobFeed(Double userLat, Double userLon, String city, String trade,
                                                String urgency, Double maxDistanceKm, int limit) {
        // Only a city was given -> use the city centre as the viewer's position
        if ((userLat == null || userLon == null) && notEmpty(city)) {
            ResolvedCity r = locationService.resolveCityCoords(city);
            userLat = r.lat();
            userLon = r.lon();
        }

        // Python: city=city if not (user_lat and user_lon) else None
        boolean haveCoordsForFilter = PyMath.truthy(userLat) && PyMath.truthy(userLon);
        List<Job> jobs = jobService.listJobs(trade, haveCoordsForFilter ? null : city, "open", urgency, null, 100, 0);

        List<WithDistance<Job>> items = new ArrayList<>();
        for (Job j : jobs) {
            Double dist = null;
            if (userLat != null && userLon != null) {
                dist = locationService.haversineDistance(userLat, userLon, j.latitude(), j.longitude());
                if (PyMath.truthy(maxDistanceKm) && dist > maxDistanceKm) {
                    continue;
                }
            }
            items.add(new WithDistance<>(j, dist));
        }

        Comparator<WithDistance<Job>> urgentFirst =
                Comparator.comparingInt(w -> "urgent".equals(w.item().urgency()) ? 0 : 1);
        if (userLat != null && userLon != null) {
            items.sort(urgentFirst.thenComparingDouble(w -> w.distanceKm() != null ? w.distanceKm() : 9999));
        } else {
            // Intended behaviour: urgent first, then newest first.
            // (The Python code applied reverse=True to the whole key, which accidentally put urgent jobs LAST.)
            items.sort(urgentFirst.thenComparing(
                    Comparator.comparing((WithDistance<Job> w) -> w.item().createdAt()).reversed()));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (WithDistance<Job> w : items.subList(0, Math.min(limit, items.size()))) {
            Map<String, Object> m = toMap(w.item());
            m.put("distance_km", w.distanceKm());
            out.add(m);
        }
        return out;
    }

    /** Available workers for an employer. Nearest first (best rated breaks ties), else best rated. */
    public List<Map<String, Object>> getWorkerFeed(Double employerLat, Double employerLon, String city,
                                                   String trade, Double maxDailyRate, Double maxDistanceKm,
                                                   int limit) {
        if ((employerLat == null || employerLon == null) && notEmpty(city)) {
            ResolvedCity r = locationService.resolveCityCoords(city);
            employerLat = r.lat();
            employerLon = r.lon();
        }

        boolean haveCoordsForFilter = PyMath.truthy(employerLat) && PyMath.truthy(employerLon);
        List<Worker> workers = workerService.listWorkers(
                trade, haveCoordsForFilter ? null : city, "available", maxDailyRate, null, 100, 0);

        List<WithDistance<Worker>> items = new ArrayList<>();
        for (Worker w : workers) {
            Double dist = null;
            if (employerLat != null && employerLon != null) {
                dist = locationService.haversineDistance(employerLat, employerLon, w.latitude(), w.longitude());
                if (PyMath.truthy(maxDistanceKm) && dist > maxDistanceKm) {
                    continue;
                }
            }
            items.add(new WithDistance<>(w, dist));
        }

        if (employerLat != null && employerLon != null) {
            items.sort(Comparator.<WithDistance<Worker>>comparingDouble(
                            w -> w.distanceKm() != null ? w.distanceKm() : 9999)
                    .thenComparing(Comparator.comparingDouble((WithDistance<Worker> w) -> w.item().rating()).reversed()));
        } else {
            items.sort(Comparator.comparingDouble((WithDistance<Worker> w) -> w.item().rating()).reversed()
                    .thenComparing(Comparator.comparingInt((WithDistance<Worker> w) -> w.item().jobsCompleted()).reversed()));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (WithDistance<Worker> w : items.subList(0, Math.min(limit, items.size()))) {
            Map<String, Object> m = toMap(w.item());
            m.put("distance_km", w.distanceKm());
            out.add(m);
        }
        return out;
    }

    /** Counts for the dashboard ticker + top trades/cities. */
    public PlatformInsights getPlatformInsights() {
        long totalWorkers = count("SELECT COUNT(*) FROM workers");
        long openJobs = count("SELECT COUNT(*) FROM jobs WHERE status = 'open'");
        long totalContacts = count("SELECT COUNT(*) FROM contacts");

        List<PlatformInsights.TradeStat> trades = jdbc.query(
                "SELECT trade, COUNT(*) AS count, AVG(daily_rate) AS avg_rate FROM workers "
                        + "GROUP BY trade ORDER BY count DESC, trade ASC LIMIT 6",
                (rs, i) -> new PlatformInsights.TradeStat(rs.getString("trade"), rs.getLong("count"), rs.getDouble("avg_rate")));

        List<PlatformInsights.CityStat> cities = jdbc.query(
                "SELECT city, COUNT(*) AS count FROM workers GROUP BY city ORDER BY count DESC, city ASC LIMIT 6",
                (rs, i) -> new PlatformInsights.CityStat(rs.getString("city"), rs.getLong("count")));

        return new PlatformInsights(totalWorkers, openJobs, totalContacts, trades, cities);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** Entity -> JSON-shaped map (snake_case keys) so we can add the extra "distance_km" field. */
    private Map<String, Object> toMap(Object entity) {
        return objectMapper.convertValue(entity, MAP_TYPE);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
