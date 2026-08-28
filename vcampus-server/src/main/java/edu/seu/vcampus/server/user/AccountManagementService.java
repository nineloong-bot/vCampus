package edu.seu.vcampus.server.user;

import edu.seu.vcampus.common.error.ErrorDetail;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.routing.ClientContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Socket-facing account operations for the first account-management slice. */
public final class AccountManagementService {
    private final AccessDatabase database;
    private final SessionRegistry sessions;
    private final PasswordHasher passwords = new PasswordHasher();

    public AccountManagementService(AccessDatabase database, SessionRegistry sessions) {
        this.database = database;
        this.sessions = sessions;
    }

    public ResponseBody<EmptyResponse> logout(Message request, ClientContext context) {
        sessions.revoke(request.sessionToken());
        return ResponseBody.success(EmptyResponse.INSTANCE);
    }

    public ResponseBody<UserView> current(Message request, ClientContext context) {
        return sessions.find(request.sessionToken()).map(session -> ResponseBody.success(session.user()))
                .orElseGet(() -> failure("AUTH_SESSION_EXPIRED", "登录已过期"));
    }

    public ResponseBody<EmptyResponse> changePassword(Message request, ClientContext context) {
        var session = sessions.find(request.sessionToken());
        if (session.isEmpty()) return failure("AUTH_SESSION_EXPIRED", "登录已过期");
        if (!(request.body() instanceof ChangePasswordCommand command)) {
            return failure("COMMON_INVALID_REQUEST", "修改密码请求格式无效");
        }
        char[] oldPassword = command.oldPassword();
        char[] newPassword = command.newPassword();
        try (Connection connection = database.open()) {
            if (newPassword == null || newPassword.length < 8
                    || !hasLetterAndDigit(newPassword)) {
                return failure("AUTH_PASSWORD_POLICY_VIOLATION", "密码需为至少8位且同时包含字母和数字");
            }
            String select = "SELECT passwordHash,passwordSalt,passwordIterations FROM tblUser WHERE userId=?";
            try (PreparedStatement query = connection.prepareStatement(select)) {
                query.setString(1, session.get().user().userId());
                try (ResultSet result = query.executeQuery()) {
                    if (!result.next() || !passwords.matches(oldPassword, result.getString(1),
                            result.getString(2), result.getInt(3))) {
                        return failure("AUTH_INVALID_CREDENTIALS", "原密码错误");
                    }
                }
            }
            PasswordHasher.PasswordHash hash = passwords.hash(newPassword);
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE tblUser SET passwordHash=?,passwordSalt=?,passwordIterations=?,"
                            + "mustChangePassword=FALSE,updatedAt=? WHERE userId=?")) {
                update.setString(1, hash.hash()); update.setString(2, hash.salt());
                update.setInt(3, hash.iterations()); update.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                update.setString(5, session.get().user().userId()); update.executeUpdate();
            }
            sessions.revoke(request.sessionToken());
            return ResponseBody.success(EmptyResponse.INSTANCE);
        } catch (SQLException error) {
            return failure("COMMON_SERVER_ERROR", "修改密码失败");
        } finally {
            if (oldPassword != null) java.util.Arrays.fill(oldPassword, '\0');
            if (newPassword != null) java.util.Arrays.fill(newPassword, '\0');
        }
    }

    public ResponseBody<UserView> register(Message request, ClientContext context) {
        if (!(request.body() instanceof TeacherAccountApplicationCommand command)) {
            return failure("COMMON_INVALID_REQUEST", "注册请求格式无效");
        }
        String loginId = normalize(command.loginId());
        char[] password = command.password();
        try (Connection connection = database.open()) {
            if (!loginId.matches("[A-Z0-9_]{4,32}") || password == null
                    || password.length < 8 || !hasLetterAndDigit(password)) {
                return failure("AUTH_PASSWORD_POLICY_VIOLATION", "账号或密码不符合规范");
            }
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT userId FROM tblUser WHERE loginId=?")) {
                query.setString(1, loginId);
                if (query.executeQuery().next()) return failure("USER_LOGIN_ID_EXISTS", "登录标识已存在");
            }
            PasswordHasher.PasswordHash hash = passwords.hash(password);
            String id = UUID.randomUUID().toString(); Timestamp now = new Timestamp(System.currentTimeMillis());
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO tblUser (userId,loginId,passwordHash,passwordSalt,passwordIterations,"
                            + "roleCode,accountStatus,mustChangePassword,failedLoginCount,rowVersion,createdAt,updatedAt)"
                            + " VALUES (?,?,?,?,?,?,?,?,0,0,?,?)")) {
                insert.setString(1, id); insert.setString(2, loginId); insert.setString(3, hash.hash());
                insert.setString(4, hash.salt()); insert.setInt(5, hash.iterations()); insert.setString(6, "TEACHER");
                insert.setString(7, "PENDING"); insert.setBoolean(8, false); insert.setTimestamp(9, now); insert.setTimestamp(10, now);
                insert.executeUpdate();
            }
            return ResponseBody.success(new UserView(id, loginId, UserRole.TEACHER));
        } catch (SQLException error) {
            return failure("COMMON_SERVER_ERROR", "注册失败");
        } finally { if (password != null) java.util.Arrays.fill(password, '\0'); }
    }

    public ResponseBody<PageResult<UserSummary>> search(Message request, ClientContext context) {
        var session = sessions.find(request.sessionToken());
        if (session.isEmpty() || session.get().user().role() != UserRole.ADMIN) {
            return failure("AUTH_FORBIDDEN", "没有账户查询权限");
        }
        UserSearchQuery query = request.body() instanceof UserSearchQuery value ? value
                : new UserSearchQuery("", null, null, 0, 50);
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(
                "SELECT userId,loginId,roleCode,accountStatus,rowVersion FROM tblUser ORDER BY loginId")) {
            ListBuilder builder = new ListBuilder(query);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) builder.add(new UserSummary(result.getString(1), result.getString(2),
                        UserRole.valueOf(result.getString(3)), AccountStatus.valueOf(result.getString(4)), result.getLong(5)));
            }
            return ResponseBody.success(new PageResult<>(builder.items, query.page(), query.pageSize(), builder.total));
        } catch (SQLException | RuntimeException error) { return failure("COMMON_SERVER_ERROR", "查询账户失败"); }
    }

    private static boolean hasLetterAndDigit(char[] value) {
        boolean letter = false, digit = false;
        for (char character : value) { letter |= Character.isLetter(character); digit |= Character.isDigit(character); }
        return letter && digit;
    }

    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }

    private static <T extends java.io.Serializable> ResponseBody<T> failure(String code, String message) {
        return ResponseBody.failure(code, message, new ErrorDetail(code, message, Map.of(), UUID.randomUUID().toString(), false));
    }

    private static final class ListBuilder {
        private final ArrayList<UserSummary> items = new ArrayList<>(); private final UserSearchQuery query; private long total;
        private ListBuilder(UserSearchQuery query) { this.query = query; }
        private void add(UserSummary value) { if (query.keyword() == null || value.loginId().contains(normalize(query.keyword()))) {
            if ((query.role() == null || query.role() == value.role()) && (query.status() == null || query.status() == value.status())) {
                if (total++ >= query.page() * query.pageSize() && items.size() < query.pageSize()) items.add(value);
            }
        }}
    }
}
