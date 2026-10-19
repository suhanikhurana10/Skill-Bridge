package com.kaayakasetu.telecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** JSON body for POST /api/v1/telecom/ivr. digits = keypad digit(s) pressed (empty on the first call). */
public record IvrRequest(
        @NotNull @JsonProperty("call_sid") String callSid,
        @NotNull @JsonProperty("caller_phone") String callerPhone,
        String digits) {
    public IvrRequest {
        if (digits == null) digits = "";
    }
}
