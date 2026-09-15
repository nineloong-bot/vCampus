package edu.seu.vcampus.server.student.handler;

/** User-module authentication contract consumed by student handlers. */
@FunctionalInterface
public interface StudentAuthorizationPort {
    /**
     * Performs the authenticate operation.
     * @param sessionToken the session token
     * @return the operation result
     */
    StudentPrincipal authenticate(String sessionToken);
}
