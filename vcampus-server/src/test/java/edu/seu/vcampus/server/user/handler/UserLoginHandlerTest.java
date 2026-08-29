package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.ChangePasswordCommand;
import edu.seu.vcampus.common.user.ChangeUserStatusCommand;
import edu.seu.vcampus.common.user.LoginCommand;
import edu.seu.vcampus.common.user.LoginResult;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UpdateUserRoleCommand;
import edu.seu.vcampus.common.user.UserSearchQuery;
import edu.seu.vcampus.common.user.UserSummary;
import edu.seu.vcampus.common.user.UserView;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.user.service.UserService;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.assertThat;

class UserLoginHandlerTest {
    private static final ClientContext CONTEXT =
            new ClientContext("connection", "127.0.0.1");

    @Test
    void concurrentLoginFailureUsesArchitectureErrorCode() {
        ResponseBody<LoginResult> response = handle(
                new ConcurrentModificationException("stale row version"));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "COMMON_CONCURRENT_MODIFICATION");
    }

    @Test
    void loginValidationFailureUsesArchitectureErrorCode() {
        ResponseBody<LoginResult> response = handle(
                new IllegalArgumentException("invalid login request"));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "COMMON_VALIDATION_FAILED");
    }

    @Test
    void internalLoginFailureIncludesTraceIdWithoutLeakingSensitiveValues() {
        String clientControlledToken =
                "eyJhbGciOiJIUzI1NiJ9eyJzdWIiOiJERU1PX0FETUlOIn0";
        ResponseBody<LoginResult> response = handle(clientControlledToken, new RuntimeException(
                "java.sql.SQLException password=Secret123 hash=PBKDF2 salt=abc token="
                        + clientControlledToken));

        assertThat(response).extracting(body -> body.success(), body -> body.code())
                .containsExactly(false, "COMMON_INTERNAL_ERROR");
        assertThat(response.toString()).doesNotContain("RuntimeException", "SQLException",
                "Secret123", "PBKDF2", "salt=abc", clientControlledToken);
        assertThat(response.error()).isNotNull();
        assertThat(response.error().traceId()).isNotBlank()
                .isNotEqualTo(clientControlledToken);
    }

    @Test
    void internalLoginFailureGeneratesTraceIdWhenRequestIdIsBlank() {
        ResponseBody<LoginResult> response = handle(" ", new RuntimeException());

        assertThat(response.error()).isNotNull();
        assertThat(response.error().traceId()).isNotBlank();
    }

    private static ResponseBody<LoginResult> handle(RuntimeException failure) {
        return handle("request-123", failure);
    }

    private static ResponseBody<LoginResult> handle(String requestId, RuntimeException failure) {
        UserLoginHandler handler = new UserLoginHandler(new ThrowingLoginService(failure));
        Message message = new Message(requestId, MessageType.REQUEST, "USER_LOGIN", null,
                new LoginCommand("DEMO_ADMIN", "DemoPass123".toCharArray(), "client"), 0);
        return handler.handle(message, CONTEXT);
    }

    private static final class ThrowingLoginService implements UserService {
        private final RuntimeException failure;

        private ThrowingLoginService(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public LoginResult login(LoginCommand command, ClientContext context) {
            throw failure;
        }

        @Override
        public UserView applyForTeacherAccount(TeacherAccountApplicationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout(String sessionToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserView getCurrentUser(String sessionToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void changePassword(String sessionToken, ChangePasswordCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<UserSummary> searchUsers(UserSearchQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserView updateRole(UpdateUserRoleCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserView changeStatus(ChangeUserStatusCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeSessionsForUser(String userId) {
            throw new UnsupportedOperationException();
        }
    }
}
