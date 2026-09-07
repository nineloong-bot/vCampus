package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe role-specific borrowing policy. */
public record LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, long rowVersion, PenaltyPolicy penalties) implements Serializable {
    public LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, rowVersion, PenaltyPolicy.defaults());
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
