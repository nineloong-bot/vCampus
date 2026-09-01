package edu.seu.vcampus.server.student.handler;

/** User-module authentication contract consumed by student handlers. */
@FunctionalInterface
public interface StudentAuthorizationPort {
    StudentPrincipal authenticate(String sessionToken);
}
