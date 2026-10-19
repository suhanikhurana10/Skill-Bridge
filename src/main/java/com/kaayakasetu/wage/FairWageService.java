package com.kaayakasetu.wage;

import com.kaayakasetu.common.Fmt;
import com.kaayakasetu.common.PyMath;
import com.kaayakasetu.location.LocationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Port of wage_service.py.
 *
 * Formula (all inputs are heuristics, not a trained ML model):
 *   daily = base(trade) x cityTierMultiplier x (1 + 3% x min(years,15)) x (1.20 if urgent) + toolPremium
 *   recommended = daily rounded to the nearest Rs.50
 *   fair band   = 85% .. 125% of recommended
 */
@Service
public class FairWageService {

    private record TradeRate(String name, double base, String category, String kn, String hi) {}

    /** Order matters: getTradeKey() returns the FIRST key that matches (same as the Python dict). */
    private static final Map<String, TradeRate> TRADE_BASE_RATES = new LinkedHashMap<>();

    private static void rate(String key, String name, double base, String category, String kn, String hi) {
        TRADE_BASE_RATES.put(key, new TradeRate(name, base, category, kn, hi));
    }

    static {
        rate("helper", "General Helper / Labour", 550.0, "Unskilled", "ಕೂಲಿ ಕಾರ್ಮಿಕ", "मजदूर / सहायक");
        rate("loader", "Loading & Shifting", 600.0, "Unskilled", "ಹಮಾಲಿ", "पल्लेदार / लोडर");
        rate("painter", "Painter", 750.0, "Semi-skilled", "ಪೇಂಟರ್", "पेंटर");
        rate("gardener", "Gardener", 650.0, "Semi-skilled", "ತೋಟಗಾರ", "माली");
        rate("plumber", "Plumber", 850.0, "Skilled", "ಪ್ಲಂಬರ್", "प्लंबर");
        rate("carpenter", "Carpenter", 900.0, "Skilled", "ಬಡಗಿ", "बढ़ई");
        rate("electrician", "Electrician", 900.0, "Skilled", "ಎಲೆಕ್ಟ್ರಿಷಿಯನ್", "इलेक्ट्रीशियन");
        rate("mason", "Mason / Mestri", 950.0, "Skilled", "ಮೇಸ್ತ್ರಿ", "राजमिस्त्री");
        rate("welder", "Welder / Fabricator", 950.0, "Skilled", "ವೆಲ್ಡರ್", "वेल्डर");
        rate("tile_mason", "Tiles / Flooring Mason", 1000.0, "High-skilled", "ಟೈಲ್ಸ್ ಮೇಸ್ತ್ರಿ", "टाइल मिस्त्री");
        rate("ac_technician", "AC / Appliance Tech", 1050.0, "High-skilled", "ಎಸಿ ಮೆಕ್ಯಾನಿಕ್", "एसी तकनीशियन");
        rate("driver", "Commercial Driver", 800.0, "Semi-skilled", "ಚಾಲಕ", "चालक / ड्राइवर");
    }

    /** City economic tier -> multiplier */
    private static final Map<String, Double> TIER_MULTIPLIERS = Map.of(
            "Tier-1", 1.25,   // metros: Bengaluru, Delhi, Mumbai
            "Tier-2", 1.00,   // Hubballi, Mysuru, Lucknow, Patna
            "Tier-3", 0.85);  // Davanagere, Ballari, Tumakuru, smaller towns

    private final LocationService locationService;

    public FairWageService(LocationService locationService) {
        this.locationService = locationService;
    }

    /** Fuzzy trade lookup: first table key that is contained in the input (or contains it); default "helper". */
    String getTradeKey(String tradeName) {
        String t = (tradeName == null ? "" : tradeName).strip().toLowerCase().replace(" ", "_");
        for (String key : TRADE_BASE_RATES.keySet()) {
            if (t.contains(key) || key.contains(t)) {
                return key;
            }
        }
        return "helper";
    }

    /** Convenience overload with the Python defaults (own tools, not urgent, 8 hours, no offer to judge). */
    public FairWageResponse predictFairWage(String trade, String city, int experienceYears) {
        return predictFairWage(trade, city, experienceYears, true, false, 8, null);
    }

    public FairWageResponse predictFairWage(String trade, String city, int experienceYears, boolean hasOwnTools,
                                            boolean isUrgent, int hoursPerDay, Double wageOffered) {
        String tradeKey = getTradeKey(trade);
        TradeRate meta = TRADE_BASE_RATES.get(tradeKey);
        double baseRate = meta.base();

        String cityTier = locationService.resolveCityCoords(city).tier();
        double tierMultiplier = TIER_MULTIPLIERS.getOrDefault(cityTier, 1.0);

        // +3% per year of experience, capped at 15 years
        int expClamped = Math.min(Math.max(experienceYears, 0), 15);
        double expMultiplier = 1.0 + (expClamped * 0.03);

        // Own-tools allowance: Rs.150 for skilled trades, Rs.80 otherwise
        double toolPremium = 0.0;
        if (hasOwnTools) {
            toolPremium = ("Skilled".equals(meta.category()) || "High-skilled".equals(meta.category())) ? 150.0 : 80.0;
        }

        double urgencyMultiplier = isUrgent ? 1.20 : 1.0;

        double calculatedDaily = (baseRate * tierMultiplier * expMultiplier * urgencyMultiplier) + toolPremium;
        double recommendedDaily = PyMath.roundToWhole(calculatedDaily / 50.0) * 50.0;
        double minFair = PyMath.roundToWhole((recommendedDaily * 0.85) / 50.0) * 50.0;
        double maxFair = PyMath.roundToWhole((recommendedDaily * 1.25) / 50.0) * 50.0;
        double hourly = PyMath.round(recommendedDaily / Math.max(hoursPerDay, 1), 1);

        String bracket = "FAIR";
        if (wageOffered != null && wageOffered > 0) {
            if (wageOffered < minFair) {
                bracket = "BELOW_FAIR_WAGE";
            } else if (wageOffered > maxFair) {
                bracket = "PREMIUM_OFFER";
            } else {
                bracket = "FAIR_MARKET_RATE";
            }
        }

        String daily = Fmt.commaF0(recommendedDaily);
        String lo = Fmt.commaF0(minFair);
        String hi = Fmt.commaF0(maxFair);

        String english = "Recommended daily wage for " + meta.name() + " in " + city + " (" + cityTier + ") "
                + "with " + experienceYears + " years experience is ₹" + daily
                + " (Fair range: ₹" + lo + " - ₹" + hi + "/day).";

        String kannada = city + " ನಗರದಲ್ಲಿ (" + cityTier + ") " + experienceYears + " ವರ್ಷಗಳ ಅನುಭವವಿರುವ "
                + meta.kn() + " ಕೆಲಸಕ್ಕೆ ನ್ಯಾಯಯುತ ದಿನಗೂಲಿ ₹" + daily + " ಆಗಿದೆ "
                + "(ಕನಿಷ್ಠ ಸೂಕ್ತ ದರ: ₹" + lo + " ರಿಂದ ₹" + hi + "/ದಿನ).";

        String hindi = city + " (" + cityTier + ") में " + experienceYears + " वर्ष के अनुभव वाले "
                + meta.hi() + " के लिए उचित दैनिक मजदूरी ₹" + daily + " है "
                + "(स्वीकार्य सीमा: ₹" + lo + " से ₹" + hi + "/दिन).";

        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("base_rate", baseRate);
        factors.put("tier_multiplier", tierMultiplier);
        factors.put("experience_multiplier", PyMath.round(expMultiplier, 2));
        factors.put("tool_premium", toolPremium);
        factors.put("urgency_multiplier", urgencyMultiplier);
        factors.put("category", meta.category());

        Map<String, String> explanations = new LinkedHashMap<>();
        explanations.put("english", english);
        explanations.put("kannada", kannada);
        explanations.put("hindi", hindi);

        return new FairWageResponse(meta.name(), city, cityTier, experienceYears, recommendedDaily, hourly,
                minFair, maxFair, bracket, factors, explanations);
    }
}
