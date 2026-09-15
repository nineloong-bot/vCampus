package edu.seu.vcampus.common.wallet;

import java.io.Serializable;
import java.io.Serial;

/** Stable operation receipt. Balance is the snapshot at the original operation. */
public record WalletOperationResult(String operationId, long balanceCents) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
