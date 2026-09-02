package edu.seu.vcampus.client.shop.service;

import java.util.Objects;

/** Client-side representation of a stable server failure code. */
public final class ShopClientException extends RuntimeException {
    private final String code;

    public ShopClientException(String code) {
        super(Objects.requireNonNull(code, "code"));
        this.code = code;
    }

    public String code() {
        return code;
    }
}
