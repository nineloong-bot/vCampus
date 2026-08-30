package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Reconfigures borrowing limits for one role. */
public record UpdateLibraryPolicyCommand(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long expectedVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
