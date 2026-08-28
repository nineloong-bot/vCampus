package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Minimal client-side gateway for authentication. */
public final class UserClient {
    private final ClientConnection connection;
    private final Duration timeout;
    private final String clientInstanceId = UUID.randomUUID().toString();

    public UserClient(ClientConnection connection, Duration timeout) {
        this.connection = connection;
        this.timeout = timeout;
    }

    /** Sends credentials to the USER_LOGIN command. */
    public CompletableFuture<ResponseBody<LoginResult>> login(String loginId, char[] password) {
        return connection.send("USER_LOGIN",
                new LoginCommand(loginId, password, clientInstanceId), timeout);
    }

    /** Submits a public teacher account application. */
    public CompletableFuture<ResponseBody<UserView>> registerTeacher(
            String loginId, char[] password) {
        return connection.send("USER_REGISTER",
                new TeacherAccountApplicationCommand(loginId, password), timeout);
    }
}
