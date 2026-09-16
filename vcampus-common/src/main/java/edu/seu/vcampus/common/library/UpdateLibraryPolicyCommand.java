package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Reconfigures borrowing limits for one role. */
public record UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, int reserveDays, long expectedVersion,
        PenaltyPolicy penalties) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long expectedVersion, PenaltyPolicy penalties) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays,
                LibraryPolicyView.DEFAULT_RESERVE_DAYS, expectedVersion, penalties);
    }

    public UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long expectedVersion) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays,
                LibraryPolicyView.DEFAULT_RESERVE_DAYS, expectedVersion, PenaltyPolicy.defaults());
    }
}
