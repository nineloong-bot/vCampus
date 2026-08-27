package edu.seu.vcampus.server.user.repository;

/** Indicates that a normalized login identifier already exists. */
public final class DuplicateLoginIdException extends RuntimeException {
    /** Creates a duplicate-login exception while retaining the database cause. */
    public DuplicateLoginIdException(Throwable cause) {
        super("Normalized login identifier already exists", cause);
    }
}
