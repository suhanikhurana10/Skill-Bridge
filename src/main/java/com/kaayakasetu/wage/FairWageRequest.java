package com.kaayakasetu.wage;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Request body for POST /api/v1/wage/predict (Python: FairWageRequest). */
public record FairWageRequest(
        @NotBlank String trade,
        @NotBlank String city,
        @Min(0) @Max(40) @JsonProperty("experience_years") Integer experienceYears,
        @JsonProperty("has_own_tools") Boolean hasOwnTools,
        // explicit name: a component called "isUrgent" would otherwise confuse JSON libraries
        @JsonProperty("is_urgent") Boolean urgent,
        @Min(1) @Max(14) @JsonProperty("hours_per_day") Integer hoursPerDay) {

    public FairWageRequest {
        if (experienceYears == null) experienceYears = 3;
        if (hasOwnTools == null) hasOwnTools = true;
        if (urgent == null) urgent = false;
        if (hoursPerDay == null) hoursPerDay = 8;
    }
}
