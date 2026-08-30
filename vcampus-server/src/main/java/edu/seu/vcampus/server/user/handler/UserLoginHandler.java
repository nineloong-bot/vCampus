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
    private final UserHandlerRejectionAuditor rejectionAuditor;

    /** Creates a login handler backed by the existing user service. */
    public UserLoginHandler(UserService users) {
        this.users = Objects.requireNonNull(users, "users");
        rejectionAuditor = new UserHandlerRejectionAuditor(users);
    }

    /** Authenticates a login command and maps invalid credentials to a safe response. */
    @Override
    public ResponseBody<LoginResult> handle(Message message, ClientContext context) {
        if (!(message.body() instanceof LoginCommand command)) {
            return rejectionAuditor.reject(null, "USER_LOGIN", null,
                    new IllegalArgumentException("COMMON_VALIDATION_FAILED"), context,
                    "登录请求未能完成");
        }
        try {
            // LoginResult contains a live session token, so USER_LOGIN intentionally
            // never enters the persistent response-snapshot deduplicator.
            return ResponseBody.success(users.login(command, context));
        } catch (RuntimeException error) {
            return UserHandlerErrorMapper.failure(error, safeMessage(error));
        } finally {
            if (command != null) command.clearPassword();
        }
    }

    private static String safeMessage(RuntimeException error) {
        if (error instanceof InvalidCredentialsException) {
            return "登录标识或密码错误";
        }
        if (error instanceof AccountPendingException
                || error instanceof AccountDisabledException
                || error instanceof AccountLockedException) {
            return "账户当前无法登录";
        }
        return "登录请求未能完成";
    }
}
