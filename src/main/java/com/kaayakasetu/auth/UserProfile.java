package com.kaayakasetu.auth;

/** What the API tells the client about the logged-in account (never the password hash). */
public record UserProfile(long id, String phone, String name, String role, Long workerId) {

    public static UserProfile from(AuthUser u) {
        return new UserProfile(u.id(), u.phone(), u.name(), u.role().name().toLowerCase(), u.workerId());
    }

    public static UserProfile from(User u) {
        return from(AuthUser.from(u));
    }
}
