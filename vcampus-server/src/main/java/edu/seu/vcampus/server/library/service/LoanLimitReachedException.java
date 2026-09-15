package edu.seu.vcampus.server.library.service;

/** Raised when a borrower has reached the configured active-loan limit. */
public final class LoanLimitReachedException extends IllegalStateException {
    /**
     * Creates a loan limit reached exception with its required collaborators.
     * @param userId the user identifier
     */
    public LoanLimitReachedException(String userId) {
        super("Library loan limit reached: " + userId);
    }
}
