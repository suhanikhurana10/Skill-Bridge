package com.kaayakasetu.worker;

/**
 * A worker row. Used both as the database model and as the JSON response (Python: WorkerResponse).
 * JSON names are snake_case through the global Jackson setting (dailyRate -> daily_rate).
 */
public record Worker(
        long id,
        String name,
        String phone,
        String trade,
        String secondarySkills,
        int experienceYears,
        double dailyRate,
        Double hourlyRate,
        String city,
        String area,
        double latitude,
        double longitude,
        String availability,
        double rating,
        int jobsCompleted,
        String languagePref,
        String source,
        String createdAt) {}
