package edu.seu.vcampus.server.library.service;

import java.util.Objects;

/** Minimal authenticated identity needed by library policy checks. */
public record BorrowerIdentity(String userId, String roleCode) {
    /**
     * Creates a borrower identifierentity with its required collaborators.
     * @param userId the user identifier
     * @param roleCode the role code
     */
    public BorrowerIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleCode, "roleCode");
    }
}
