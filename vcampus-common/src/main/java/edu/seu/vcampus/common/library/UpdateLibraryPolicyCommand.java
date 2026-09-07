package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Reconfigures borrowing limits for one role. */
public record UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long expectedVersion, PenaltyPolicy penalties) implements Serializable {
    public UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long expectedVersion) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, expectedVersion, PenaltyPolicy.defaults());
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
