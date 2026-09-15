package edu.seu.vcampus.server.shop.port;

import java.util.Objects;

/** Provides shop access exception behavior. */
public final class ShopAccessException extends RuntimeException {
    private final String code;

    /**
     * Creates a shop access exception with its required collaborators.
     * @param code the code
     */
    public ShopAccessException(String code) {
        super(Objects.requireNonNull(code, "code"));
        this.code = code;
    }

    /**
     * Performs the code operation.
     * @return the operation result
     */
    public String code() {
        return code;
    }
}
