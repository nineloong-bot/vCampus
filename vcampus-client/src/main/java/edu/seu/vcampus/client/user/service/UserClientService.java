package edu.seu.vcampus.client.user.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Sends user requests through the shared client connection. */
public class UserClientService {
    private static final String USER_LOGIN = "USER_LOGIN";

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
        return connection.<LoginResult>send(USER_LOGIN, command, timeout)
                .thenApply(this::requireSuccess);
    }

    private LoginResult requireSuccess(ResponseBody<LoginResult> response) {
        if (!response.success() || response.data() == null) {
            throw new IllegalArgumentException(response.code());
        }
        LoginResult result = response.data();
        connection.setSessionToken(result.sessionToken());
        return result;
    }
}
