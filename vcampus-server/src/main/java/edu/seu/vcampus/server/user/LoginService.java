package edu.seu.vcampus.server.user;

import edu.seu.vcampus.common.error.ErrorDetail;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.routing.ClientContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Authenticates the demo account stored in Access. */
public final class LoginService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginService.class);
    private static final Logger SECURITY_LOGGER = LoggerFactory.getLogger("vcampus.security");
    private final ConnectionProvider connections;
    private final PasswordHasher passwords = new PasswordHasher();

    public LoginService(ConnectionProvider connections) {
        this.connections = connections;
    }

    /** Handles one USER_LOGIN request without exposing credential details. */
    public ResponseBody<LoginResult> login(Message request, ClientContext context) {
        if (!(request.body() instanceof LoginCommand command)) {
            SECURITY_LOGGER.warn("action=USER_LOGIN result=COMMON_INVALID_REQUEST client={} traceId={}",
                    context.clientAddress(), request.requestId());
            return failure("COMMON_INVALID_REQUEST", "登录请求格式无效", request.requestId(), false);
        }
        char[] password = command.password();
        String loginId = normalize(command.loginId());
        try {
            if (loginId.isEmpty() || password == null || password.length == 0) {
                logRejected(loginId, context, request, "AUTH_INVALID_CREDENTIALS");
                return invalidCredentials(request.requestId());
            }
            Account account = find(loginId);
            if (account == null || !passwords.matches(password, account.passwordHash(),
                    account.passwordSalt(), account.passwordIterations())) {
                logRejected(loginId, context, request, "AUTH_INVALID_CREDENTIALS");
                return invalidCredentials(request.requestId());
            }
            if (!"ACTIVE".equals(account.status())) {
                logRejected(loginId, context, request, "AUTH_ACCOUNT_DISABLED");
                return failure("AUTH_ACCOUNT_DISABLED", "账户当前不可登录",
                        request.requestId(), false);
            }
            LoginResult result = new LoginResult(
                    UUID.randomUUID().toString(),
                    new UserView(account.userId(), account.loginId(),
                            UserRole.valueOf(account.roleCode())),
                    Set.of(),
                    account.mustChangePassword());
            SECURITY_LOGGER.info(
                    "action=USER_LOGIN result=SUCCESS loginId={} userId={} client={} traceId={}",
                    loginId, account.userId(), context.clientAddress(), request.requestId());
            return ResponseBody.success(result);
        } catch (SQLException | RuntimeException error) {
            LOGGER.error("登录处理失败，traceId={}", request.requestId(), error);
            SECURITY_LOGGER.error(
                    "action=USER_LOGIN result=COMMON_SERVER_ERROR loginId={} client={} traceId={}",
                    loginId, context.clientAddress(), request.requestId());
            return failure("COMMON_SERVER_ERROR", "服务器暂时无法处理登录",
                    request.requestId(), true);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private Account find(String loginId) throws SQLException {
        String sql = "SELECT userId, loginId, passwordHash, passwordSalt, passwordIterations, "
                + "roleCode, accountStatus, mustChangePassword FROM tblUser WHERE loginId = ?";
        try (Connection connection = connections.open();
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, loginId);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new Account(
                        result.getString("userId"),
                        result.getString("loginId"),
                        result.getString("passwordHash"),
                        result.getString("passwordSalt"),
                        result.getInt("passwordIterations"),
                        result.getString("roleCode"),
                        result.getString("accountStatus"),
                        result.getBoolean("mustChangePassword"));
            }
        }
    }

    private static String normalize(String loginId) {
        return loginId == null ? "" : loginId.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseBody<LoginResult> invalidCredentials(String traceId) {
        return failure("AUTH_INVALID_CREDENTIALS", "账号或密码错误", traceId, false);
    }

    private static void logRejected(String loginId, ClientContext context,
            Message request, String result) {
        SECURITY_LOGGER.warn(
                "action=USER_LOGIN result={} loginId={} client={} traceId={}",
                result, loginId, context.clientAddress(), request.requestId());
    }

    private static ResponseBody<LoginResult> failure(
            String code, String message, String traceId, boolean retryable) {
        ErrorDetail detail = new ErrorDetail(code, message, Map.of(), traceId, retryable);
        return ResponseBody.failure(code, message, detail);
    }

    private record Account(
            String userId,
            String loginId,
            String passwordHash,
            String passwordSalt,
            int passwordIterations,
            String roleCode,
            String status,
            boolean mustChangePassword) {
    }
}
