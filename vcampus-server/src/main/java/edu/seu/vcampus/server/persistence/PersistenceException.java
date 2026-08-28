package edu.seu.vcampus.server.persistence;

/** Unchecked boundary exception for persistence infrastructure failures. */
public final class PersistenceException extends RuntimeException {
    /** Creates an exception without exposing database details to callers. */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
