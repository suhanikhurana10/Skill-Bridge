package com.kaayakasetu.job;

import com.fasterxml.jackson.annotation.JsonProperty;
/** Request body for PATCH /api/v1/jobs/{id}. */
public record JobUpdateRequest(
        String title,
        String trade,
        String description,
        String city,
        String area,
        @JsonProperty("wage_offered") Double wageOffered,
        String urgency,
        @JsonProperty("workers_needed") Integer workersNeeded,
        String status) {}
