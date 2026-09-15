package edu.seu.vcampus.server.student.repository;

/** Stable module exception for organization persistence failures. */
public final class OrganizationPersistenceException extends RuntimeException {
    /**
     * Creates a organization persistence exception with its required collaborators.
     * @param message the message
     * @param cause the cause
     */
    public OrganizationPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
