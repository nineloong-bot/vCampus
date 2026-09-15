package edu.seu.vcampus.common.wallet;

import java.io.Serializable;
import java.io.Serial;

/** Own wallet entry; no counterparty identity is exposed. */
public record WalletEntryView(String operationId, String type, long deltaCents, long balanceCents, String orderKey, java.time.Instant createdAt) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
}
