package edu.seu.vcampus.server.security;

/** Indicates that valid credentials belong to a temporarily locked account. */
public final class AccountLockedException extends RuntimeException {
    /** Creates the stable account-lock error. */
    public AccountLockedException() { super("AUTH_ACCOUNT_LOCKED"); }
}
