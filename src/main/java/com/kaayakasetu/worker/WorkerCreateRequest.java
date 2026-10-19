package com.kaayakasetu.worker;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request body for POST /api/v1/workers/ (Python: WorkerCreate/WorkerBase).
 * Validation annotations replace Pydantic's Field(ge=0, le=50, gt=0 ...).
 * The compact constructor fills in the defaults Pydantic had (experience 1, 'available', 'kannada', 'web').
 */
public record WorkerCreateRequest(
        @NotBlank String name,
        @NotBlank String phone,
        @NotBlank String trade,
        @JsonProperty("secondary_skills") String secondarySkills,
        @Min(0) @Max(50) @JsonProperty("experience_years") Integer experienceYears,
        @NotNull @Positive @JsonProperty("daily_rate") Double dailyRate,
        @Positive @JsonProperty("hourly_rate") Double hourlyRate,
        @NotBlank String city,
        String area,
        Double latitude,
        Double longitude,
        String availability,
        @JsonProperty("language_pref") String languagePref,
        String source) {

    public WorkerCreateRequest {
        if (experienceYears == null) experienceYears = 1;
        if (availability == null) availability = "available";
        if (languagePref == null) languagePref = "kannada";
        if (source == null) source = "web";
    }
}
