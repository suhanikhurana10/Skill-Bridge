package com.kaayakasetu.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body of POST /api/v1/auth/register. role is "worker" or "employer". */
public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "phone must be a 10-digit mobile number") String phone,
        // BCrypt only looks at the first 72 bytes, so longer passwords are refused instead of silently cut
        @NotBlank @Size(min = 8, max = 72, message = "password must be 8 to 72 characters") String password,
        @NotBlank @Size(max = 255) String name,
        @NotBlank String role) {}
