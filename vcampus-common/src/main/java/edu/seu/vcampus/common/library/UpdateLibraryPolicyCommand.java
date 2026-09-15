package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Reconfigures borrowing limits for one role. */
/**
 * Carries immutable update library policy command data.
 * @param roleCode the role code
 * @param maxActiveLoans the max active loans
 * @param loanDays the loan days
 * @param maxRenewals the max renewals
 * @param renewalDays the renewal days
 * @param expectedVersion the expected version
 * @param penalties the penalties
 */
public record UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long expectedVersion, PenaltyPolicy penalties) implements Serializable {
    /**
     * Validates and creates a update library policy command.
     * @param roleCode the role code
     * @param maxActiveLoans the max active loans
     * @param loanDays the loan days
     * @param maxRenewals the max renewals
     * @param renewalDays the renewal days
     * @param expectedVersion the expected version
     */
    public UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long expectedVersion) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, expectedVersion, PenaltyPolicy.defaults());
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
