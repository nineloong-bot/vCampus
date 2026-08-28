package edu.seu.vcampus.server.library.service;

/** Raised when a borrower attempts to operate on another user's loan. */
public final class LoanOwnershipException extends SecurityException {
    public LoanOwnershipException(String loanId) {
        super("Library loan belongs to another user: " + loanId);
    }
}
