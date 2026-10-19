package com.kaayakasetu.contact;

import jakarta.validation.constraints.NotBlank;

/** Request body for PATCH /api/v1/contact/{id} : status "accepted" or "declined". */
public record ContactStatusUpdate(@NotBlank String status) {}
