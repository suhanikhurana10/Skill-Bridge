package com.kaayakasetu.location;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/location")
@Tag(name = "Location Microservice")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /** Supported hubs across Karnataka and Hindi-speaking regions with economic tiers. */
    @GetMapping("/cities")
    public List<CityInfo> supportedCities() {
        return locationService.listSupportedCities();
    }

    /** Geodesic distance between two points, in km. */
    @GetMapping("/distance")
    public Map<String, Object> distance(@RequestParam("lat1") double lat1,
                                        @RequestParam("lon1") double lon1,
                                        @RequestParam("lat2") double lat2,
                                        @RequestParam("lon2") double lon2) {
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("lat", lat1);
        origin.put("lon", lon1);
        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("lat", lat2);
        destination.put("lon", lon2);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", origin);
        body.put("destination", destination);
        body.put("distance_km", locationService.haversineDistance(lat1, lon1, lat2, lon2));
        return body;
    }

    /** Resolve a city name to coordinates, state and tier (falls back to Bengaluru if unknown). */
    @GetMapping("/geocode")
    public Object geocode(@RequestParam("city") String city) {
        return locationService.getCityInfo(city).<Object>map(info -> info).orElseGet(() -> {
            ResolvedCity r = locationService.resolveCityCoords(city);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", city);
            body.put("lat", r.lat());
            body.put("lon", r.lon());
            body.put("tier", r.tier());
            body.put("is_default_fallback", true);
            return body;
        });
    }
}
