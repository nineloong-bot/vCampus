package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe role-specific borrowing policy. */
public record LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long rowVersion) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
