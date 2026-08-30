package edu.seu.vcampus.server.library.service;

/** Raised when a physical copy cannot currently be borrowed. */
public final class CopyUnavailableException extends IllegalStateException {
    public CopyUnavailableException(String copyId) {
        super("Library copy is unavailable: " + copyId);
    }
}
