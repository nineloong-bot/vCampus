package edu.seu.vcampus.client.shop.ui;

/** Extracts the stable code returned by the Shop protocol. */
public final class ShopUiErrors {
    private ShopUiErrors() { }

    public static String code(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }

    public static boolean sessionExpired(String code) {
        return "AUTH_SESSION_EXPIRED".equals(code);
    }
}
