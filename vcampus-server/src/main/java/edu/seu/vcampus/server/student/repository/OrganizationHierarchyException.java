package edu.seu.vcampus.server.student.repository;

/** Raised when an organization change would leave active descendants orphaned. */
public final class OrganizationHierarchyException extends RuntimeException {
    /**
     * Creates a organization hierarchy exception with its required collaborators.
     * @param message the message
     */
    public OrganizationHierarchyException(String message) {
        super(message);
    }
}
