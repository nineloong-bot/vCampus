package edu.seu.vcampus.server.student.repository;

/** Stable module exception for organization persistence failures. */
public final class OrganizationPersistenceException extends RuntimeException {
    public OrganizationPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
