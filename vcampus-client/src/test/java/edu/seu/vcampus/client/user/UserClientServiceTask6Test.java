package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.TeacherAccountApplicationCommand;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserClientServiceTask6Test {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void teacherApplicationUsesPublicRegisterAndClearsAllPasswordMaterial() {
        ClientConnection connection = mock(ClientConnection.class);
        UserView expected = mock(UserView.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)))
                .when(connection).send(eq("USER_REGISTER"),
                        any(TeacherAccountApplicationCommand.class), eq(TIMEOUT));
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);
        char[] password = "Teacher123456".toCharArray();

        assertThat(users.applyForTeacherAccount("teacher", password).join())
                .isSameAs(expected);

        ArgumentCaptor<TeacherAccountApplicationCommand> command =
                ArgumentCaptor.forClass(TeacherAccountApplicationCommand.class);
        verify(connection).send(eq("USER_REGISTER"), command.capture(), eq(TIMEOUT));
        assertThat(password).containsOnly('\0');
        assertThat(command.getValue().password()).containsOnly('\0');
    }

    @Test
    void securityAuditSearchUsesSeparateReadCommandAndTypedPage() {
        ClientConnection connection = mock(ClientConnection.class);
        SecurityAuditQuery query = new SecurityAuditQuery(
                null, null, null, null, null, 0, 20);
        PageResult<SecurityAuditView> expected = new PageResult<>(List.of(), 0, 20, 0);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)))
                .when(connection).send("SECURITY_AUDIT_SEARCH", query, TIMEOUT);
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);

        assertThat(users.searchSecurityAudits(query).join()).isEqualTo(expected);
        verify(connection).send("SECURITY_AUDIT_SEARCH", query, TIMEOUT);
    }

    @Test
    void currentUserMapsOnlyExplicitSessionExpiryToAClientInternalException() {
        ClientConnection connection = mock(ClientConnection.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                "AUTH_SESSION_EXPIRED", "会话已失效", null)))
                .when(connection).send(eq("USER_GET_CURRENT"), any(), eq(TIMEOUT));
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);

        assertThatThrownBy(() -> users.getCurrentUser().join())
                .isInstanceOf(CompletionException.class)
                .satisfies(error -> assertThat(error.getCause().getClass().getSimpleName())
                        .isEqualTo("SessionExpiredClientException"));
    }

    @Test
    void studentPasswordResetSendsOnlyTargetAndVersion() {
        ClientConnection connection = mock(ClientConnection.class);
        ResetStudentPasswordCommand command =
                new ResetStudentPasswordCommand("student", 7);
        UserView expected = mock(UserView.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)))
                .when(connection).send("USER_RESET_STUDENT_PASSWORD", command, TIMEOUT);
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);

        assertThat(users.resetStudentPassword(command).join()).isSameAs(expected);
        verify(connection).send("USER_RESET_STUDENT_PASSWORD", command, TIMEOUT);
        assertThat(command.toString()).doesNotContain("12345678", "password", "hash", "salt");
    }
}
