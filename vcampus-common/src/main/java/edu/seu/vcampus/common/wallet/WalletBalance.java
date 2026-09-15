package edu.seu.vcampus.common.wallet;

import java.io.Serializable;
import java.io.Serial;

/** Read-only virtual currency snapshot; all amounts are integer cents. */
public record WalletBalance(long balanceCents, long pendingCents, int version) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
