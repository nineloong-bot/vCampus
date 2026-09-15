package edu.seu.vcampus.server.library.service;

/** Raised when an overdue loan cannot be renewed. */
public final class LoanOverdueException extends IllegalStateException {
    /**
     * Creates a loan overdue exception with its required collaborators.
     * @param loanId the loan identifier
     */
    public LoanOverdueException(String loanId) {
        super("Library loan is overdue: " + loanId);
    }
}
