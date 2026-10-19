package com.kaayakasetu.job;

/** A job posting row / JSON response (Python: JobResponse). */
public record Job(
        long id,
        String title,
        String trade,
        String description,
        String employerName,
        String employerPhone,
        String city,
        String area,
        double latitude,
        double longitude,
        int durationDays,
        double wageOffered,
        String paymentType,
        String urgency,
        int workersNeeded,
        String status,
        String createdAt) {}
