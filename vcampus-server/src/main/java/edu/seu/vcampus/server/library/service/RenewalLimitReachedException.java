package edu.seu.vcampus.server.library.service;

/** Raised when a loan has exhausted its configured renewal allowance. */
public final class RenewalLimitReachedException extends IllegalStateException {
    public RenewalLimitReachedException(String loanId) {
        super("Library renewal limit reached: " + loanId);
    }
}
