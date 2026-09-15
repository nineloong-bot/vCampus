package edu.seu.vcampus.server.library.service;

/** Raised when an operation requires an active loan. */
public final class LoanNotActiveException extends IllegalStateException {
    /**
     * Creates a loan not active exception with its required collaborators.
     * @param loanId the loan identifier
     */
    public LoanNotActiveException(String loanId) {
        super("Library loan is not active: " + loanId);
    }
}
