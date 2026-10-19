package com.kaayakasetu.matching;

/** Why a worker and a job got the score they did (all scores are 0-100). */
public record MatchScoreBreakdown(
        double skillScore,
        double distanceScore,
        double wageScore,
        double experienceScore,
        double availabilityScore,
        double totalScore,
        double distanceKm,
        String explanation) {}
