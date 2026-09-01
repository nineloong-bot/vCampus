package edu.seu.vcampus.server.student.repository;

/** Raised when an organization change would leave active descendants orphaned. */
public final class OrganizationHierarchyException extends RuntimeException {
    public OrganizationHierarchyException(String message) {
        super(message);
    }
}
