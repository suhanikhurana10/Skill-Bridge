package com.kaayakasetu.job;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Request body for POST /api/v1/jobs/ (Python: JobCreate/JobBase). */
public record JobCreateRequest(
        @NotBlank String title,
        @NotBlank String trade,
        String description,
        @NotBlank @JsonProperty("employer_name") String employerName,
        @NotBlank @JsonProperty("employer_phone") String employerPhone,
        @NotBlank String city,
        String area,
        Double latitude,
        Double longitude,
        @Min(1) @JsonProperty("duration_days") Integer durationDays,
        @NotNull @Positive @JsonProperty("wage_offered") Double wageOffered,
        @JsonProperty("payment_type") String paymentType,
        String urgency,
        @Min(1) @JsonProperty("workers_needed") Integer workersNeeded) {

    public JobCreateRequest {
        if (durationDays == null) durationDays = 1;
        if (paymentType == null) paymentType = "daily";
        if (urgency == null) urgency = "normal";
        if (workersNeeded == null) workersNeeded = 1;
    }
}
