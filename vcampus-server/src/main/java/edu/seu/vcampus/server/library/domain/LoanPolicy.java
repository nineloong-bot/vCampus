package edu.seu.vcampus.server.library.domain;

/** Borrowing limits configured for one user role. */
public record LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long rowVersion, edu.seu.vcampus.common.library.PenaltyPolicy penalties) {
    /**
     * Creates a loan policy with its required collaborators.
     * @param policyId the policy identifier
     * @param roleCode the role code
     * @param maxActiveLoans the max active loans
     * @param loanDays the loan days
     * @param maxRenewals the max renewals
     * @param renewalDays the renewal days
     * @param rowVersion the row version
     */
    public LoanPolicy(String policyId, String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion) {
        this(policyId, roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, rowVersion,
                edu.seu.vcampus.common.library.PenaltyPolicy.defaults());
    }
}
