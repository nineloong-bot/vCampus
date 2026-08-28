package edu.seu.vcampus.server.library.service;

/** Raised when a borrower has reached the configured active-loan limit. */
public final class LoanLimitReachedException extends IllegalStateException {
    public LoanLimitReachedException(String userId) {
        super("Library loan limit reached: " + userId);
    }
}
