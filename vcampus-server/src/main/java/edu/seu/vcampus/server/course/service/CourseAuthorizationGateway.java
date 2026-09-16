package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.server.course.domain.CourseForbiddenException;

/** Course-owned adapter boundary for resolving authenticated sessions. */
@FunctionalInterface
public interface CourseAuthorizationGateway {
    /** Resolves a session token to the minimal identity required by enrollment. */
    CourseSessionIdentity requireSession(String sessionToken);

    /** Verifies an assigned user role for administrative course maintenance. */
    default void requireUserRole(String userId, String expectedRole) {
        throw new CourseForbiddenException();
    }

    /** Resolves a safe account label for read-only course projections. */
    default String userDisplayName(String userId) { return userId; }
}
