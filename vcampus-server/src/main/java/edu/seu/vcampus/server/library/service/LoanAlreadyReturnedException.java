package edu.seu.vcampus.server.library.service;

/** Raised when an already returned loan receives another return request. */
public final class LoanAlreadyReturnedException extends IllegalStateException {
    public LoanAlreadyReturnedException(String loanId) {
        super("Library loan is already returned: " + loanId);
    }
}
