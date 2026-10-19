package com.kaayakasetu.worker;

import com.fasterxml.jackson.annotation.JsonProperty;
/** Request body for PATCH /api/v1/workers/{id}: every field optional, only non-null ones are applied. */
public record WorkerUpdateRequest(
        String name,
        String trade,
        @JsonProperty("secondary_skills") String secondarySkills,
        @JsonProperty("experience_years") Integer experienceYears,
        @JsonProperty("daily_rate") Double dailyRate,
        @JsonProperty("hourly_rate") Double hourlyRate,
        String city,
        String area,
        Double latitude,
        Double longitude,
        String availability,
        @JsonProperty("language_pref") String languagePref) {}
