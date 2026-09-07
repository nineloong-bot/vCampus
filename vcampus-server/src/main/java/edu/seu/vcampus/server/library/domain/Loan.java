package edu.seu.vcampus.server.library.domain;

import edu.seu.vcampus.common.library.LoanStatus;

import java.time.Instant;

/** Persistent borrowing history for one physical copy. */
public record Loan(String loanId, String copyId, String borrowerUserId, Instant borrowedAt,
        Instant dueAt, Instant returnedAt, int renewCount, LoanStatus status, long rowVersion,
        String borrowerRoleCode, java.math.BigDecimal overdueFine, java.math.BigDecimal damageFine,
        edu.seu.vcampus.common.library.ReturnCondition returnCondition) {
    public Loan(String loanId, String copyId, String borrowerUserId, Instant borrowedAt,
            Instant dueAt, Instant returnedAt, int renewCount, LoanStatus status, long rowVersion) {
        this(loanId, copyId, borrowerUserId, borrowedAt, dueAt, returnedAt, renewCount, status,
                rowVersion, "STUDENT", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                edu.seu.vcampus.common.library.ReturnCondition.NORMAL);
    }
}
