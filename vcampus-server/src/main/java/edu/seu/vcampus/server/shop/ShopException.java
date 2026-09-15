package edu.seu.vcampus.server.shop;

import edu.seu.vcampus.common.shop.ShopErrorCode;

import java.util.Objects;

/** Shop-domain failure carrying a stable protocol error code. */
public final class ShopException extends RuntimeException {
    private final ShopErrorCode code;

    /**
     * Creates a shop exception with its required collaborators.
     * @param code the code
     * @param message the message
     */
    public ShopException(ShopErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Performs the code operation.
     * @return the operation result
     */
    public ShopErrorCode code() {
        return code;
    }
}
