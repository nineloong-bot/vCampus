package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.CommandNotFoundException;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.AuthorizationService;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;
import edu.seu.vcampus.server.security.InvalidCredentialsException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.session.SessionRegistry;
import edu.seu.vcampus.server.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserHandlersTest {
    private static final ClientContext CONTEXT = new ClientContext("connection", "127.0.0.1");
    private final UserService users = new StubUsers();
    private AuthorizationPort authorization;
    private MessageRouter router;

    @BeforeEach
    void registerHandlers() {
        authorization = new TrackingAuthorization();
        router = new MessageRouter(Map.of());
        new UserHandlers(router, users, authorization);
    }

    @Test
    void registersOnlyTheEightPublicUserCommands() {
        assertThat(route("USER_REGISTER", new TeacherAccountApplicationCommand("TEACHER", "Pass1234".toCharArray())).success()).isTrue();
        assertThat(route("USER_LOGIN", new LoginCommand("ADMIN", "Admin1234".toCharArray(), "client")).success()).isTrue();
        assertThat(route("USER_LOGOUT", EmptyRequest.INSTANCE).success()).isTrue();
        assertThat(route("USER_GET_CURRENT", EmptyRequest.INSTANCE).success()).isTrue();
        assertThat(route("USER_CHANGE_PASSWORD", new ChangePasswordCommand("OldPass123".toCharArray(), "NewPass123".toCharArray())).success()).isTrue();
        assertThat(route("USER_SEARCH", new UserSearchQuery(null, null, null, 0, 10)).success()).isTrue();
        assertThat(route("USER_UPDATE_ROLE", new UpdateUserRoleCommand("user", UserRole.TEACHER, 0)).success()).isTrue();
        assertThat(route("USER_CHANGE_STATUS", new ChangeUserStatusCommand("user", AccountStatus.DISABLED, "reviewed", 0)).success()).isTrue();
        assertThatThrownBy(() -> route("USER_CREATE_STUDENT", EmptyResponse.INSTANCE))
                .isInstanceOf(CommandNotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource({"USER_SEARCH,USER_READ_ALL", "USER_UPDATE_ROLE,USER_ROLE_WRITE", "USER_CHANGE_STATUS,USER_STATUS_WRITE"})
    void adminCommandsRequireTheirPermission(String command, String permission) {
        assertThat(route(command, bodyFor(command)).success()).isTrue();

        assertThat(((TrackingAuthorization) authorization).permission).isEqualTo(permission);
    }

    @ParameterizedTest
    @CsvSource({"USER_GET_CURRENT", "USER_CHANGE_PASSWORD"})
    void protectedRestrictedSessionCommandsUseSessionValidation(String command) {
        assertThat(route(command, bodyFor(command)).success()).isTrue();

        assertThat(((TrackingAuthorization) authorization).sessionCalls).isEqualTo(1);
        assertThat(((TrackingAuthorization) authorization).permission).isNull();
    }

    @ParameterizedTest
    @CsvSource({"USER_LOGOUT", "USER_GET_CURRENT"})
    void emptyRequestCommandsRejectEmptyResponseAsRequestBody(String command) {
        ResponseBody<?> response = route(command, EmptyResponse.INSTANCE);

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "COMMON_VALIDATION_FAILED");
    }

    @ParameterizedTest
    @CsvSource({"USER_GET_CURRENT", "USER_CHANGE_PASSWORD", "USER_SEARCH",
            "USER_UPDATE_ROLE", "USER_CHANGE_STATUS"})
    void malformedProtectedBodiesAreRejectedBeforeAuthorization(String command) {
        RejectingAuthorization rejecting = new RejectingAuthorization();
        MessageRouter bodyFirstRouter = new MessageRouter(Map.of());
        new UserHandlers(bodyFirstRouter, users, rejecting);

        ResponseBody<?> response = route(bodyFirstRouter, command, null,
                EmptyResponse.INSTANCE);

        assertThat(new Object[]{response.success(), response.code(), rejecting.calls})
                .containsExactly(false, "COMMON_VALIDATION_FAILED", 0);
    }

    @ParameterizedTest
    @CsvSource({"USER_SEARCH,USER_READ_ALL", "USER_UPDATE_ROLE,USER_ROLE_WRITE", "USER_CHANGE_STATUS,USER_STATUS_WRITE"})
    void restrictedSessionsReceiveInitialPasswordChangeError(String command, String permission) {
        ((TrackingAuthorization) authorization).restricted = true;

        ResponseBody<?> response = route(command, bodyFor(command));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void logoutIsIdempotentAfterTheFirstCallRevokesTheToken() {
        SessionRegistry sessions = new SessionRegistry();
        String token = sessions.create(identity(false));
        UserService revokingUsers = new StubUsers() {
            @Override public void logout(String sessionToken) { sessions.revoke(sessionToken); }
        };
        MessageRouter idempotentRouter = new MessageRouter(Map.of());
        new UserHandlers(idempotentRouter, revokingUsers, new AuthorizationService(sessions));

        assertThat(route(idempotentRouter, "USER_LOGOUT", token, EmptyRequest.INSTANCE).success())
                .isTrue();
        assertThat(route(idempotentRouter, "USER_LOGOUT", token, EmptyRequest.INSTANCE).success())
                .isTrue();
    }

    @Test
    void passwordChangePreservesInvalidCredentialsErrorCode() {
        UserService invalidOldPassword = new StubUsers() {
            @Override public void changePassword(String sessionToken, ChangePasswordCommand command) {
                throw new InvalidCredentialsException();
            }
        };
        MessageRouter errorRouter = new MessageRouter(Map.of());
        new UserHandlers(errorRouter, invalidOldPassword, new TrackingAuthorization());

        ResponseBody<?> response = route(errorRouter, "USER_CHANGE_PASSWORD", "token",
                new ChangePasswordCommand("WrongPass1".toCharArray(), "NewPass123".toCharArray()));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void concurrentModificationUsesTheArchitectureErrorCode() {
        UserService conflictingUsers = new StubUsers() {
            @Override public UserView updateRole(UpdateUserRoleCommand command) {
                throw new ConcurrentModificationException("stale row version");
            }
        };
        MessageRouter errorRouter = new MessageRouter(Map.of());
        new UserHandlers(errorRouter, conflictingUsers, new TrackingAuthorization());

        ResponseBody<?> response = route(errorRouter, "USER_UPDATE_ROLE", "token",
                new UpdateUserRoleCommand("user", UserRole.TEACHER, 0));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "COMMON_CONCURRENT_MODIFICATION");
    }

    @Test
    void malformedBodyReturnsSafeValidationFailure() {
        ResponseBody<?> response = route("USER_UPDATE_ROLE", EmptyRequest.INSTANCE);

        assertThat(response).extracting(body -> body.success(), body -> body.code(),
                        body -> body.data())
                .containsExactly(false, "COMMON_VALIDATION_FAILED", null);
    }

    @Test
    void internalFailureIncludesTraceIdWithoutLeakingExceptionOrSensitiveValues() {
        UserService failingUsers = new StubUsers() {
            @Override public UserView updateRole(UpdateUserRoleCommand command) {
                throw new RuntimeException(
                        "java.sql.SQLException password=Secret123 hash=PBKDF2 token=full-token");
            }
        };
        MessageRouter errorRouter = new MessageRouter(Map.of());
        new UserHandlers(errorRouter, failingUsers, new TrackingAuthorization());

        ResponseBody<?> response = route(errorRouter, "USER_UPDATE_ROLE", "full-token",
                new UpdateUserRoleCommand("user", UserRole.TEACHER, 0));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "COMMON_INTERNAL_ERROR");
        assertThat(response.toString()).doesNotContain("RuntimeException", "SQLException",
                "Secret123", "PBKDF2", "full-token");
        assertThat(response.error()).isNotNull();
        assertThat(response.error().traceId()).isNotBlank().isNotEqualTo("request");
    }

    private ResponseBody<?> route(String command, Serializable body) {
        return route(router, command, "token", body);
    }

    private static ResponseBody<?> route(MessageRouter target, String command, String token,
                                           Serializable body) {
        return target.route(new Message("request", MessageType.REQUEST, command, token, body, 0),
                CONTEXT);
    }

    private static Serializable bodyFor(String command) {
        return switch (command) {
            case "USER_LOGOUT", "USER_GET_CURRENT" -> EmptyRequest.INSTANCE;
            case "USER_CHANGE_PASSWORD" -> new ChangePasswordCommand("OldPass123".toCharArray(), "NewPass123".toCharArray());
            case "USER_SEARCH" -> new UserSearchQuery(null, null, null, 0, 10);
            case "USER_UPDATE_ROLE" -> new UpdateUserRoleCommand("user", UserRole.TEACHER, 0);
            case "USER_CHANGE_STATUS" -> new ChangeUserStatusCommand("user", AccountStatus.DISABLED, "reviewed", 0);
            default -> throw new IllegalArgumentException(command);
        };
    }

    private static UserIdentity identity(boolean restricted) {
        return new UserIdentity("user", "USER", UserRole.ADMIN, Set.of("USER_READ_ALL"), restricted);
    }

    private static class StubUsers implements UserService {
        private static final UserView VIEW = new UserView("user", "USER", UserRole.ADMIN,
                AccountStatus.ACTIVE, false, null, 0, LocalDateTime.MIN, LocalDateTime.MIN);

        @Override public UserView applyForTeacherAccount(TeacherAccountApplicationCommand command) { return VIEW; }
        @Override public LoginResult login(LoginCommand command, ClientContext context) { return new LoginResult("opaque", VIEW, Set.of(), false); }
        @Override public void logout(String sessionToken) { }
        @Override public UserView getCurrentUser(String sessionToken) { return VIEW; }
        @Override public void changePassword(String sessionToken, ChangePasswordCommand command) { }
        @Override public void revokeSessionsForUser(String userId) { }
        @Override public PageResult<UserSummary> searchUsers(UserSearchQuery query) { return new PageResult<>(java.util.List.of(), 0, 10, 0); }
        @Override public UserView updateRole(UpdateUserRoleCommand command) { return VIEW; }
        @Override public UserView changeStatus(ChangeUserStatusCommand command) { return VIEW; }
    }

    private static final class TrackingAuthorization implements AuthorizationPort {
        private String permission;
        private int sessionCalls;
        private boolean restricted;

        @Override public UserIdentity requireSession(String sessionToken) {
            sessionCalls++;
            return identity(restricted);
        }

        @Override public void requirePermission(String sessionToken, String permissionCode) {
            permission = permissionCode;
            if (restricted) throw new InitialPasswordChangeRequiredException();
        }
    }

    private static final class RejectingAuthorization implements AuthorizationPort {
        private int calls;

        @Override public UserIdentity requireSession(String sessionToken) {
            calls++;
            throw new InitialPasswordChangeRequiredException();
        }

        @Override public void requirePermission(String sessionToken, String permissionCode) {
            calls++;
            throw new InitialPasswordChangeRequiredException();
        }
    }
}
