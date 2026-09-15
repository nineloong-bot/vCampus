package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe role-specific borrowing policy. */
/**
 * Carries immutable library policy view data.
 * @param roleCode the role code
 * @param maxActiveLoans the max active loans
 * @param loanDays the loan days
 * @param maxRenewals the max renewals
 * @param renewalDays the renewal days
 * @param rowVersion the row version
 * @param penalties the penalties
 */
public record LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long rowVersion, PenaltyPolicy penalties) implements Serializable {
    /**
     * Validates and creates a library policy view.
     * @param roleCode the role code
     * @param maxActiveLoans the max active loans
     * @param loanDays the loan days
     * @param maxRenewals the max renewals
     * @param renewalDays the renewal days
     * @param rowVersion the row version
     */
    public LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, rowVersion, PenaltyPolicy.defaults());
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
