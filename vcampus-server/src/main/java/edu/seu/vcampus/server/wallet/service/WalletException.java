package edu.seu.vcampus.server.wallet.service;

/** Business failure with a stable, client-safe error code. */
public final class WalletException extends RuntimeException {
    /** Creates a failure identified by its public code. */
    public WalletException(String code) { super(code); }
}
