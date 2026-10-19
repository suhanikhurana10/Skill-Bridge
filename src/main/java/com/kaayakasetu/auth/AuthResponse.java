package com.kaayakasetu.auth;

/** JSON: access_token, token_type, expires_in, user. The client sends "Authorization: Bearer <access_token>". */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserProfile user) {}
