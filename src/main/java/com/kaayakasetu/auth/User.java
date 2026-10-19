package com.kaayakasetu.auth;

/** A row of the users table. passwordHash is a BCrypt hash - the real password is never stored. */
public record User(
        long id,
        String phone,
        String passwordHash,
        String name,
        Role role,
        Long workerId,
        String createdAt) {}
