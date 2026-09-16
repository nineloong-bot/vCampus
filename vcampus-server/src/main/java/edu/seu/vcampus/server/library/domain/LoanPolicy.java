package edu.seu.vcampus.server.library.domain;

/** Borrowing limits configured for one user role. */
public record LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, int reserveDays, long rowVersion,
        edu.seu.vcampus.common.library.PenaltyPolicy penalties) {
    public static final int DEFAULT_RESERVE_DAYS = 3;

    public LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion,
            edu.seu.vcampus.common.library.PenaltyPolicy penalties) {
        this(policyId, roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays,
                DEFAULT_RESERVE_DAYS, rowVersion, penalties);
    }

    public LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion) {
        this(policyId, roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays,
                DEFAULT_RESERVE_DAYS, rowVersion,
                edu.seu.vcampus.common.library.PenaltyPolicy.defaults());
    }
}
