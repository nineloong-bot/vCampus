package edu.seu.vcampus.server.library.domain;

/** Borrowing limits configured for one user role. */
public record LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long rowVersion) {
}
