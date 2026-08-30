package edu.seu.vcampus.server.library.domain;

import edu.seu.vcampus.common.library.LoanStatus;

import java.time.Instant;

/** Persistent borrowing history for one physical copy. */
public record Loan(String loanId, String copyId, String borrowerUserId, Instant borrowedAt,
        Instant dueAt, Instant returnedAt, int renewCount, LoanStatus status, long rowVersion) {
}
