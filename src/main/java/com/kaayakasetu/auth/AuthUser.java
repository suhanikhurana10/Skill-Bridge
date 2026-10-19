package com.kaayakasetu.auth;

/**
 * The logged-in person for the current request (what the JWT filter puts into the SecurityContext).
 * Controllers receive it with  @AuthenticationPrincipal AuthUser user.
 * For a WORKER, workerId is the id of their worker profile (null until they create it).
 * For an EMPLOYER, "user.id()" is what the jobs they posted point to (jobs.owner_user_id).
 */
public record AuthUser(long id, String phone, String name, Role role, Long workerId) {

    public static AuthUser from(User u) {
        return new AuthUser(u.id(), u.phone(), u.name(), u.role(), u.workerId());
    }

    public boolean isWorkerProfile(long id) {
        return role == Role.WORKER && workerId != null && workerId == id;
    }

    public boolean isEmployer(long userId) {
        return role == Role.EMPLOYER && this.id == userId;
    }
}
