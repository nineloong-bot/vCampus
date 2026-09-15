package edu.seu.vcampus.server.library.service;

/** Raised when an already returned loan receives another return request. */
public final class LoanAlreadyReturnedException extends IllegalStateException {
    /**
     * Creates a loan already returned exception with its required collaborators.
     * @param loanId the loan identifier
     */
    public LoanAlreadyReturnedException(String loanId) {
        super("Library loan is already returned: " + loanId);
    }
}
