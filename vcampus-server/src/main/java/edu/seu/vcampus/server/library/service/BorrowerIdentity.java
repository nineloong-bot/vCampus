package edu.seu.vcampus.server.library.service;

import java.util.Objects;

/** Minimal authenticated identity needed by library policy checks. */
public record BorrowerIdentity(String userId, String roleCode) {
    public BorrowerIdentity {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleCode, "roleCode");
    }
}
