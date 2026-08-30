package edu.seu.vcampus.server.security;

/** Indicates that valid credentials belong to a pending account. */
public final class AccountPendingException extends RuntimeException {
    /** Creates the stable pending-account error. */
    public AccountPendingException() { super("AUTH_ACCOUNT_PENDING"); }
}
