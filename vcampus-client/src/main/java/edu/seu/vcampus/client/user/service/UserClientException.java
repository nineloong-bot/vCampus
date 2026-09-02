package edu.seu.vcampus.client.user.service;

import java.util.Objects;

/** Typed user-client failure that preserves the server's stable error code. */
public final class UserClientException extends RuntimeException {
    private final String code;

    public UserClientException(String code, String message) {
        super(message == null || message.isBlank() ? "用户请求失败" : message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public String code() {
        return code;
    }
}
