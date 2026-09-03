package edu.seu.vcampus.client.user.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.common.paging.PageResult;

import java.time.Duration;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Sends user requests through the shared client connection. */
public class UserClientService {
    private static final String USER_LOGIN = "USER_LOGIN";
    private static final String USER_CHANGE_PASSWORD = "USER_CHANGE_PASSWORD";
    private static final String USER_LOGOUT = "USER_LOGOUT";
    private static final String USER_REGISTER = "USER_REGISTER";
    private static final String USER_GET_CURRENT = "USER_GET_CURRENT";
    private static final String USER_SEARCH = "USER_SEARCH";
    private static final String USER_UPDATE_ROLE = "USER_UPDATE_ROLE";
    private static final String USER_CHANGE_STATUS = "USER_CHANGE_STATUS";
    private static final String USER_RESET_STUDENT_PASSWORD =
            "USER_RESET_STUDENT_PASSWORD";
    private static final String USER_RESET_TEACHER_PASSWORD =
            "USER_RESET_TEACHER_PASSWORD";
    private static final String SECURITY_AUDIT_SEARCH = "SECURITY_AUDIT_SEARCH";

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
        return this.<LoginResult>sendAsync(USER_LOGIN, command, command::clearPassword)
                .thenApply(this::requireLoginSuccess);
    }

    /** Submits a public teacher-account application and clears all password copies. */
    public CompletableFuture<UserView> applyForTeacherAccount(
            String loginId, char[] password) {
        Objects.requireNonNull(password, "password");
        TeacherAccountApplicationCommand command;
        try {
            command = new TeacherAccountApplicationCommand(loginId, password);
        } finally {
            Arrays.fill(password, '\0');
        }
        return this.<UserView>sendAsync(USER_REGISTER, command, command::clearPassword)
                .thenApply(UserClientService::requireSuccess);
    }

    /** Gets the current safe account projection. */
    public CompletableFuture<UserView> getCurrentUser() {
        return this.<UserView>sendAsync(USER_GET_CURRENT, EmptyRequest.INSTANCE, () -> { })
                .thenApply(UserClientService::requireCurrentUserSuccess);
    }

    /** Searches safe user summaries using server-side paging and filters. */
    public CompletableFuture<PageResult<UserSummary>> searchUsers(UserSearchQuery query) {
        return this.<PageResult<UserSummary>>sendAsync(USER_SEARCH, query, () -> { })
                .thenApply(UserClientService::requireSuccess);
    }

    /** Retained compatibility call for the permanently retired role-change command. */
    public CompletableFuture<UserView> updateRole(UpdateUserRoleCommand command) {
        return this.<UserView>sendAsync(USER_UPDATE_ROLE, command, () -> { })
                .thenApply(UserClientService::requireSuccess);
    }

    /** Requests administrator-controlled initialization of a student's password. */
    public CompletableFuture<UserView> resetStudentPassword(
            ResetStudentPasswordCommand command) {
        return this.<UserView>sendAsync(USER_RESET_STUDENT_PASSWORD, command, () -> { })
                .thenApply(UserClientService::requireSuccess);
    }

    /** Requests administrator-controlled initialization of a teacher's password. */
    public CompletableFuture<UserView> resetTeacherPassword(
            ResetTeacherPasswordCommand command) {
        return this.<UserView>sendAsync(USER_RESET_TEACHER_PASSWORD, command, () -> { })
                .thenApply(UserClientService::requireSuccess);
    }

    /** Updates an account lifecycle status using optimistic locking. */
    public CompletableFuture<UserView> changeStatus(ChangeUserStatusCommand command) {
        return this.<UserView>sendAsync(USER_CHANGE_STATUS, command, () -> { })
                .thenApply(UserClientService::requireSuccess);
    }

    /** Searches sanitized audit records through the separate read-only command. */
    public CompletableFuture<PageResult<SecurityAuditView>> searchSecurityAudits(
            SecurityAuditQuery query) {
        return this.<PageResult<SecurityAuditView>>sendAsync(
                        SECURITY_AUDIT_SEARCH, query, () -> { })
                .thenApply(UserClientService::requireSuccess);
    }

    /** Changes the password and clears the revoked local session after success. */
    public CompletableFuture<Void> changePassword(char[] oldPassword, char[] newPassword) {
        Objects.requireNonNull(oldPassword, "oldPassword");
        Objects.requireNonNull(newPassword, "newPassword");
        ChangePasswordCommand command;
        try {
            command = new ChangePasswordCommand(oldPassword, newPassword);
        } finally {
            Arrays.fill(oldPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
        return this.<EmptyResponse>sendAsync(
                        USER_CHANGE_PASSWORD, command, command::clearPasswords)
                .thenApply(UserClientService::requireSessionSuccess)
                .thenRun(this::clearSession);
    }

    /** Logs out asynchronously and clears local credentials even if the server is unavailable. */
    public CompletableFuture<Void> logout() {
        return this.<EmptyResponse>sendAsync(USER_LOGOUT, EmptyRequest.INSTANCE, () -> { })
                .thenApply(UserClientService::requireSuccess)
                .thenAccept(ignored -> { })
                .whenComplete((ignored, failure) -> clearSession());
    }

    /** Removes the in-memory session token without logging or persisting it. */
    public void clearSession() {
        connection.setSessionToken(null);
    }

    private <T extends Serializable> CompletableFuture<ResponseBody<T>> sendAsync(
            String command, Serializable body, Runnable cleanup) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return connection.<T>send(command, body, timeout);
            } finally {
                cleanup.run();
            }
        }).thenCompose(response -> response);
    }

    private LoginResult requireLoginSuccess(ResponseBody<LoginResult> response) {
        if (!response.success() || response.data() == null) {
            throw new IllegalArgumentException(response.code());
        }
        LoginResult result = response.data();
        connection.setSessionToken(result.sessionToken());
        return result;
    }

    private static <T extends java.io.Serializable> T requireSuccess(ResponseBody<T> response) {
        if (!response.success() || response.data() == null) {
            throw new IllegalArgumentException(response.code());
        }
        return response.data();
    }

    private static UserView requireCurrentUserSuccess(ResponseBody<UserView> response) {
        return requireSessionSuccess(response);
    }

    private static <T extends Serializable> T requireSessionSuccess(ResponseBody<T> response) {
        if (!response.success()
                && "AUTH_SESSION_REVOKED_PASSWORD_RESET".equals(response.code())) {
            throw new PasswordResetSessionClientException();
        }
        if (!response.success() && "AUTH_SESSION_EXPIRED".equals(response.code())) {
            throw new SessionExpiredClientException();
        }
        return requireSuccess(response);
    }
}
