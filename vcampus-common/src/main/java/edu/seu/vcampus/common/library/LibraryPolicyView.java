package edu.seu.vcampus.common.library;

import java.io.Serial;
import java.io.Serializable;

/** Client-safe role-specific borrowing policy. */
public record LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
        int maxRenewals, int renewalDays, int reserveDays, long rowVersion,
        PenaltyPolicy penalties) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_RESERVE_DAYS = 3;

    public LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion, PenaltyPolicy penalties) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, DEFAULT_RESERVE_DAYS,
                rowVersion, penalties);
    }

    public LibraryPolicyView(String roleCode, int maxActiveLoans, int loanDays,
            int maxRenewals, int renewalDays, long rowVersion) {
        this(roleCode, maxActiveLoans, loanDays, maxRenewals, renewalDays, DEFAULT_RESERVE_DAYS,
                rowVersion, PenaltyPolicy.defaults());
    }
}
