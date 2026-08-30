package edu.seu.vcampus.server.security;

/** Indicates that valid credentials belong to a disabled or cancelled account. */
public final class AccountDisabledException extends RuntimeException {
    /** Creates the stable disabled-account error. */
    public AccountDisabledException() { super("AUTH_ACCOUNT_DISABLED"); }
}
