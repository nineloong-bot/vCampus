package edu.seu.vcampus.server.wallet.service;

import edu.seu.vcampus.common.wallet.WalletBalance;

/** Read-only balance boundary for trusted account services. */
public interface WalletQueryPort {
    /** Returns the balance for a server-authorized identity without creating wallet rows. */
    WalletBalance getBalance(String userId);
}
