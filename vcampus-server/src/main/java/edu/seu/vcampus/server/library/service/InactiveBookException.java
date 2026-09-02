package edu.seu.vcampus.server.library.service;

/** Raised when an inactive title is used for a new circulation operation. */
public final class InactiveBookException extends IllegalStateException {
    public InactiveBookException(String bookId) { super("Book is inactive: " + bookId); }
}
