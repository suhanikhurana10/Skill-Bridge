package com.kaayakasetu.wage;

import java.util.Map;

/** Result of a wage prediction (JSON keys are snake_case, e.g. recommended_daily_wage). */
public record FairWageResponse(
        String trade,
        String city,
        String cityTier,
        int experienceYears,
        double recommendedDailyWage,
        double recommendedHourlyWage,
        double fairWageMin,
        double fairWageMax,
        String wageBracket,
        Map<String, Object> factors,
        Map<String, String> regionalExplanations) {}
