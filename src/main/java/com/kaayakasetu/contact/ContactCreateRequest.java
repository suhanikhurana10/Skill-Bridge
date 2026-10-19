package com.kaayakasetu.contact;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/contact/ .
 * The SENDER is not in the body any more: it is always the logged-in user (taken from the JWT), so nobody can
 * send a message "as" somebody else.
 *  - a WORKER applies for a job:  receiver_type "employer" + job_id
 *  - an EMPLOYER contacts a worker: receiver_type "worker" + receiver_id (+ optional job_id of their own job)
 */
public record ContactCreateRequest(
        @NotBlank @JsonProperty("receiver_type") String receiverType,
        @JsonProperty("receiver_id") Long receiverId,
        @JsonProperty("job_id") Long jobId,
        String message) {}
