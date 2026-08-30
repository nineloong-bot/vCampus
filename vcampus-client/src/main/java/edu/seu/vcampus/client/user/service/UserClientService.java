package edu.seu.vcampus.client.user.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;

import java.io.Serializable;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Sends user requests through the shared client connection. */
public class UserClientService {
    private static final String USER_LOGIN = "USER_LOGIN";
    private static final String USER_CHANGE_PASSWORD = "USER_CHANGE_PASSWORD";
    private static final String USER_LOGOUT = "USER_LOGOUT";

    private final ClientConnection connection;
    private final String clientInstanceId;
    private final Duration timeout;

    /** Creates the demo user client service. */
    public UserClientService(
            ClientConnection connection, String clientInstanceId, Duration timeout) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.clientInstanceId = Objects.requireNonNull(clientInstanceId, "clientInstanceId");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /** Logs in and stores the returned opaque session token on success. */
    public CompletableFuture<LoginResult> login(String loginId, char[] password) {
        Objects.requireNonNull(password, "password");
        LoginCommand command;
        try {
            command = new LoginCommand(loginId, password, clientInstanceId);
        } finally {
            Arrays.fill(password, '\0');
        }
        return this.<LoginResult>sendAsync(USER_LOGIN, command)
                .thenApply(this::requireSuccess);
    }

    /** Changes the current user's password and invalidates the local session on success. */
    public CompletableFuture<Void> changePassword(char[] oldPassword, char[] newPassword) {
        ChangePasswordCommand command;
        try {
            command = new ChangePasswordCommand(
                    Objects.requireNonNull(oldPassword, "oldPassword"),
                    Objects.requireNonNull(newPassword, "newPassword"));
        } finally {
            clearPassword(oldPassword);
            clearPassword(newPassword);
        }
        return this.<EmptyResponse>sendAsync(USER_CHANGE_PASSWORD, command)
                .thenApply(response -> {
                    requireEmptySuccess(response);
                    connection.setSessionToken(null);
                    return null;
                });
    }

    /** Logs out the current user and invalidates the local session on server confirmation. */
    public CompletableFuture<Void> logout() {
        return this.<EmptyResponse>sendAsync(USER_LOGOUT, EmptyResponse.INSTANCE)
                .thenApply(response -> {
                    requireEmptySuccess(response);
                    connection.setSessionToken(null);
                    return null;
                });
    }

    private LoginResult requireSuccess(ResponseBody<LoginResult> response) {
        if (!response.success() || response.data() == null) {
            throw new IllegalArgumentException(response.code());
        }
        LoginResult result = response.data();
        connection.setSessionToken(result.sessionToken());
        return result;
    }

    private <T extends Serializable> CompletableFuture<ResponseBody<T>> sendAsync(
            String command, Serializable body) {
        return CompletableFuture.supplyAsync(
                        () -> connection.<T>send(command, body, timeout))
                .thenCompose(response -> response);
    }

    private static void requireEmptySuccess(ResponseBody<EmptyResponse> response) {
        if (!response.success() || response.data() != EmptyResponse.INSTANCE) {
            throw new IllegalArgumentException(response.code());
        }
    }

    private static void clearPassword(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
