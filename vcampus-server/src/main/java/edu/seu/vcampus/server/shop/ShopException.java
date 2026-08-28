package edu.seu.vcampus.server.shop;

import edu.seu.vcampus.common.shop.ShopErrorCode;

import java.util.Objects;

/** Shop-domain failure carrying a stable protocol error code. */
public final class ShopException extends RuntimeException {
    private final ShopErrorCode code;

    public ShopException(ShopErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ShopErrorCode code() {
        return code;
    }
}
