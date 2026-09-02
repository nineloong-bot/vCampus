package edu.seu.vcampus.server.library.service;

/** Raised when an overdue loan prevents a user from borrowing again. */
public final class UserHasOverdueLoansException extends IllegalStateException {
    public UserHasOverdueLoansException(String userId) {
        super("Borrower has overdue library loans: " + userId);
    }
}
