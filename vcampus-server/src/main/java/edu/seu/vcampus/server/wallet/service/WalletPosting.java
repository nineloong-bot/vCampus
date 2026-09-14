package edu.seu.vcampus.server.wallet.service;

/** Internal order instruction. Never accepted directly from a client. */
public record WalletPosting(String businessKey, String orderKey, String buyerId,
                            String sellerId, long amountCents, Kind kind) {
    /** Allowed escrow transitions. */
    public enum Kind { HOLD, REFUND, SETTLE }
    /** Validates stable order identities and positive exact amounts. */
    public WalletPosting {
        WalletRules.key(businessKey, 100); WalletRules.key(orderKey, 64);
        WalletRules.key(buyerId, 36); WalletRules.key(sellerId, 36);
        WalletRules.cents(amountCents);
        if (kind == null) throw new WalletException("WALLET_INVALID_REQUEST");
    }
}
