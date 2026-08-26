package edu.seu.vcampus.server.course.service;

/** Course-owned adapter boundary for resolving authenticated sessions. */
@FunctionalInterface
public interface CourseAuthorizationGateway {
    /** Resolves a session token to the minimal identity required by enrollment. */
    CourseSessionIdentity requireSession(String sessionToken);
}
