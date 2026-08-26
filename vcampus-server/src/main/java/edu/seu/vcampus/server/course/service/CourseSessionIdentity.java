package edu.seu.vcampus.server.course.service;

import java.util.Objects;

/** Minimal authenticated identity consumed by the course module. */
public record CourseSessionIdentity(String userId, String role) {
    /** Requires the upstream authorization adapter to return a complete identity. */
    public CourseSessionIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
    }
}
