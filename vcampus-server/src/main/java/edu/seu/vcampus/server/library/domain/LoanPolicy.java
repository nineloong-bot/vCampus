package edu.seu.vcampus.server.library.domain;

/** Borrowing limits configured for one user role. */
public record LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long rowVersion, edu.seu.vcampus.common.library.PenaltyPolicy penalties) {
    public LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion) {
        this(policyId, roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, rowVersion,
                edu.seu.vcampus.common.library.PenaltyPolicy.defaults());
    }
}
