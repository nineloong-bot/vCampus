package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.security.AccountDisabledException;
import edu.seu.vcampus.server.security.AccountLockedException;
import edu.seu.vcampus.server.security.AccountPendingException;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.user.service.UserService;

import java.util.Objects;

/** Internal Socket adapter for the public USER_LOGIN command. */
public final class UserLoginHandler implements MessageHandler {
    private final UserService users;

    /** Creates a login handler backed by the existing user service. */
    public UserLoginHandler(UserService users) {
        this.users = Objects.requireNonNull(users, "users");
    }

    /** Authenticates a login command and maps invalid credentials to a safe response. */
    @Override
    public ResponseBody<LoginResult> handle(Message message, ClientContext context) {
        LoginCommand command = (LoginCommand) message.body();
        try {
            return ResponseBody.success(users.login(command, context));
        } catch (InvalidCredentialsException error) {
            return failure(error.getMessage(), "登录标识或密码错误");
        } catch (AccountPendingException | AccountDisabledException | AccountLockedException error) {
            return failure(error.getMessage(), "账户当前无法登录");
        }
    }

    private static ResponseBody<LoginResult> failure(String code, String message) {
        return ResponseBody.failure(code, message, null);
    }
}
