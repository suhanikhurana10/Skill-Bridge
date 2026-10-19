package com.kaayakasetu.feed;

import com.kaayakasetu.common.RequestChecks;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feeds")
@Tag(name = "Feeds & Insights Microservice")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /** Personalised job feed for workers (proximity + urgency sorting). */
    @GetMapping("/jobs")
    public List<Map<String, Object>> jobFeed(
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lon", required = false) Double lon,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "trade", required = false) String trade,
            @RequestParam(name = "urgency", required = false) String urgency,
            @RequestParam(name = "max_distance_km", required = false) Double maxDistanceKm,
            @RequestParam(name = "limit", defaultValue = "30") int limit) {
        RequestChecks.range("limit", limit, 1, 100);
        return feedService.getJobFeed(lat, lon, city, trade, urgency, maxDistanceKm, limit);
    }

    /** Curated worker feed for employers. */
    @GetMapping("/workers")
    public List<Map<String, Object>> workerFeed(
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lon", required = false) Double lon,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "trade", required = false) String trade,
            @RequestParam(name = "max_daily_rate", required = false) Double maxDailyRate,
            @RequestParam(name = "max_distance_km", required = false) Double maxDistanceKm,
            @RequestParam(name = "limit", defaultValue = "30") int limit) {
        RequestChecks.range("limit", limit, 1, 100);
        return feedService.getWorkerFeed(lat, lon, city, trade, maxDailyRate, maxDistanceKm, limit);
    }

    /** Marketplace metrics: workers, open jobs, contacts, top trades and cities. */
    @GetMapping("/insights")
    public PlatformInsights insights() {
        return feedService.getPlatformInsights();
    }
}
