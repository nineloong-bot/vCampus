package edu.seu.vcampus.server.library.service;

/** Raised when a catalog write would reuse an existing ISBN. */
public final class DuplicateIsbnException extends IllegalStateException {
    public DuplicateIsbnException(String isbn) {
        super("Duplicate ISBN: " + isbn);
    }
}
