package com.kaayakasetu.location;

import com.kaayakasetu.common.PyMath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Port of location_service.py: city table, Haversine distance, city -> coordinates lookup. */
@Service
public class LocationService {

    /** LinkedHashMap keeps insertion order, which matters for listSupportedCities() (first spelling wins). */
    private static final Map<String, CityInfo> CITIES_DB = new LinkedHashMap<>();

    private static void city(String key, String name, String state, double lat, double lon, String tier, String lang) {
        CITIES_DB.put(key, new CityInfo(name, state, lat, lon, tier, lang));
    }

    static {
        // Karnataka (Kannada focus)
        city("bengaluru", "Bengaluru", "Karnataka", 12.9716, 77.5946, "Tier-1", "kannada");
        city("bangalore", "Bengaluru", "Karnataka", 12.9716, 77.5946, "Tier-1", "kannada");
        city("mysuru", "Mysuru", "Karnataka", 12.2958, 76.6394, "Tier-2", "kannada");
        city("mysore", "Mysuru", "Karnataka", 12.2958, 76.6394, "Tier-2", "kannada");
        city("hubballi", "Hubballi", "Karnataka", 15.3647, 75.1240, "Tier-2", "kannada");
        city("hubli", "Hubballi", "Karnataka", 15.3647, 75.1240, "Tier-2", "kannada");
        city("belagavi", "Belagavi", "Karnataka", 15.8497, 74.4977, "Tier-2", "kannada");
        city("mangaluru", "Mangaluru", "Karnataka", 12.9141, 74.8560, "Tier-2", "kannada");
        city("kalaburagi", "Kalaburagi", "Karnataka", 17.3297, 76.8343, "Tier-2", "kannada");
        city("davanagere", "Davanagere", "Karnataka", 14.4644, 75.9218, "Tier-3", "kannada");
        city("ballari", "Ballari", "Karnataka", 15.1394, 76.9214, "Tier-3", "kannada");
        city("shimoga", "Shivamogga", "Karnataka", 13.9299, 75.5681, "Tier-3", "kannada");
        city("shivamogga", "Shivamogga", "Karnataka", 13.9299, 75.5681, "Tier-3", "kannada");
        city("tumakuru", "Tumakuru", "Karnataka", 13.3409, 77.1010, "Tier-3", "kannada");
        city("udupi", "Udupi", "Karnataka", 13.3409, 74.7421, "Tier-3", "kannada");

        // North / Central India (Hindi focus)
        city("delhi", "Delhi", "Delhi", 28.6139, 77.2090, "Tier-1", "hindi");
        city("lucknow", "Lucknow", "Uttar Pradesh", 26.8467, 80.9462, "Tier-2", "hindi");
        city("kanpur", "Kanpur", "Uttar Pradesh", 26.4499, 80.3319, "Tier-2", "hindi");
        city("patna", "Patna", "Bihar", 25.5941, 85.1376, "Tier-2", "hindi");
        city("varanasi", "Varanasi", "Uttar Pradesh", 25.3176, 82.9739, "Tier-2", "hindi");
        city("jaipur", "Jaipur", "Rajasthan", 26.9124, 75.7873, "Tier-2", "hindi");
        city("indore", "Indore", "Madhya Pradesh", 22.7196, 75.8577, "Tier-2", "hindi");
        city("bhopal", "Bhopal", "Madhya Pradesh", 23.2599, 77.4126, "Tier-2", "hindi");
        city("ranchi", "Ranchi", "Jharkhand", 23.3441, 85.3096, "Tier-2", "hindi");
        city("agra", "Agra", "Uttar Pradesh", 27.1767, 78.0081, "Tier-2", "hindi");

        // Other major metros
        city("mumbai", "Mumbai", "Maharashtra", 19.0760, 72.8777, "Tier-1", "hindi");
        city("hyderabad", "Hyderabad", "Telangana", 17.3850, 78.4867, "Tier-1", "hindi");
    }

    /** Great-circle distance in km between two coordinates (Haversine formula), rounded to 2 decimals. */
    public double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return PyMath.round(earthRadiusKm * c, 2);
    }

    /** City name -> coordinates and tier. Unknown cities fall back to Bengaluru's centre (as in the Python version). */
    public ResolvedCity resolveCityCoords(String cityName) {
        CityInfo c = CITIES_DB.get(normalise(cityName));
        if (c != null) {
            return new ResolvedCity(c.lat(), c.lon(), c.tier());
        }
        return new ResolvedCity(12.9716, 77.5946, "Tier-2");
    }

    public Optional<CityInfo> getCityInfo(String cityName) {
        return Optional.ofNullable(CITIES_DB.get(normalise(cityName)));
    }

    /** One entry per city name (so "bangalore" and "bengaluru" are not listed twice). */
    public List<CityInfo> listSupportedCities() {
        Map<String, CityInfo> unique = new LinkedHashMap<>();
        for (CityInfo c : CITIES_DB.values()) {
            unique.putIfAbsent(c.name(), c);
        }
        return new ArrayList<>(unique.values());
    }

    private static String normalise(String cityName) {
        return cityName == null ? "" : cityName.strip().toLowerCase(Locale.ROOT);
    }
}
