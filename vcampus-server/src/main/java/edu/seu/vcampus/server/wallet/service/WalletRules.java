package edu.seu.vcampus.server.wallet.service;

import java.math.BigDecimal;

/** Shared money and key validation, independent of transport and persistence. */
public final class WalletRules {
    /** Storage ceiling in integer cents, chosen to fit DECIMAL(15,0) exactly. */
    public static final long MAX_CENTS = 999_999_999_999_999L;
    private WalletRules() { }
    /** Converts a valid virtual recharge to exact cents. */
    public static long rechargeCents(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(1000)) > 0
                || value.scale() > 2) throw new WalletException("WALLET_INVALID_AMOUNT");
        try { return value.movePointRight(2).longValueExact(); }
        catch (ArithmeticException error) { throw new WalletException("WALLET_INVALID_AMOUNT"); }
    }
    /** Rejects missing, overlong or control-character keys without normalizing identities. */
    public static void key(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) throw new WalletException("WALLET_INVALID_REQUEST");
    }
    /** Validates an internal posting amount expressed in exact cents. */
    public static void cents(long value) {
        if (value <= 0 || value > MAX_CENTS) throw new WalletException("WALLET_INVALID_AMOUNT");
    }
}
