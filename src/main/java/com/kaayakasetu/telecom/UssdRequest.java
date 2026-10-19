package com.kaayakasetu.telecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** JSON body for POST /api/v1/telecom/ussd. user_input is the dialled string ("*345#") or the digit pressed. */
public record UssdRequest(
        @NotNull @JsonProperty("session_id") String sessionId,
        @NotNull @JsonProperty("phone_number") String phoneNumber,
        @JsonProperty("user_input") String userInput) {
    public UssdRequest {
        if (userInput == null) userInput = "";
    }
}
