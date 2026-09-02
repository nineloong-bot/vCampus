package edu.seu.vcampus.server.shop.port;

import java.util.Objects;

public final class ShopAccessException extends RuntimeException {
    private final String code;

    public ShopAccessException(String code) {
        super(Objects.requireNonNull(code, "code"));
        this.code = code;
    }

    public String code() {
        return code;
    }
}
