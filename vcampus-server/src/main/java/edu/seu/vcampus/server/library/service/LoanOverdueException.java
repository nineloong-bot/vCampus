package edu.seu.vcampus.server.library.service;

/** Raised when an overdue loan cannot be renewed. */
public final class LoanOverdueException extends IllegalStateException {
    public LoanOverdueException(String loanId) {
        super("Library loan is overdue: " + loanId);
    }
}
