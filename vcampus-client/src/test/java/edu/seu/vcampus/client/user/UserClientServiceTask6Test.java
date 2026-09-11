package edu.seu.vcampus.client.user;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.ModuleAdministrationSnapshot;
import edu.seu.vcampus.common.governance.RemoveModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.SwapModuleAdministratorsCommand;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.common.user.ResetStudentPasswordCommand;
import edu.seu.vcampus.common.user.ResetTeacherPasswordCommand;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.verifyNoInteractions;

class UserClientServiceTask6Test {
    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void retiredTeacherApplicationClearsPasswordWithoutSendingARequest() {
        ClientConnection connection = mock(ClientConnection.class);
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);
        char[] password = "Teacher123456".toCharArray();

        assertThatThrownBy(() -> users.applyForTeacherAccount(
                "teacher", password).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("COMMON_VALIDATION_FAILED");

        assertThat(password).containsOnly('\0');
        verifyNoInteractions(connection);
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

    @Test
    void teacherPasswordResetUsesItsIndependentCommand() {
        ClientConnection connection = mock(ClientConnection.class);
        ResetTeacherPasswordCommand command =
                new ResetTeacherPasswordCommand("teacher", 9);
        UserView expected = mock(UserView.class);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)))
                .when(connection).send("USER_RESET_TEACHER_PASSWORD", command, TIMEOUT);
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);

        assertThat(users.resetTeacherPassword(command).join()).isSameAs(expected);
        verify(connection).send("USER_RESET_TEACHER_PASSWORD", command, TIMEOUT);
        assertThat(command.toString())
                .doesNotContain("12345678", "password", "hash", "salt", "token");
    }

    @Test
    void moduleAdministrationUsesFourDedicatedGovernanceCommands() {
        ClientConnection connection = mock(ClientConnection.class);
        ModuleAdministrationSnapshot snapshot = new ModuleAdministrationSnapshot(List.of());
        AssignModuleAdministratorCommand assign =
                new AssignModuleAdministratorCommand("COURSE", "first", 1);
        RemoveModuleAdministratorCommand remove =
                new RemoveModuleAdministratorCommand("COURSE", "first", 2);
        SwapModuleAdministratorsCommand swap = new SwapModuleAdministratorsCommand(
                "COURSE", "first", 2, "USER", "second", 3);
        doReturn(CompletableFuture.completedFuture(ResponseBody.success(snapshot)))
                .when(connection).send(eq("PLATFORM_MODULE_ADMIN_LIST"), any(), eq(TIMEOUT));
        doReturn(CompletableFuture.completedFuture(
                ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("PLATFORM_MODULE_ADMIN_ASSIGN"), eq(assign), eq(TIMEOUT));
        doReturn(CompletableFuture.completedFuture(
                ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("PLATFORM_MODULE_ADMIN_REMOVE"), eq(remove), eq(TIMEOUT));
        doReturn(CompletableFuture.completedFuture(
                ResponseBody.success(EmptyResponse.INSTANCE)))
                .when(connection).send(eq("PLATFORM_MODULE_ADMIN_SWAP"), eq(swap), eq(TIMEOUT));
        UserClientService users = new UserClientService(connection, "client", TIMEOUT);

        assertThat(users.listModuleAdministrators().join()).isEqualTo(snapshot);
        users.assignModuleAdministrator(assign).join();
        users.removeModuleAdministrator(remove).join();
        users.swapModuleAdministrators(swap).join();

        verify(connection).send(eq("PLATFORM_MODULE_ADMIN_LIST"), any(), eq(TIMEOUT));
        verify(connection).send("PLATFORM_MODULE_ADMIN_ASSIGN", assign, TIMEOUT);
        verify(connection).send("PLATFORM_MODULE_ADMIN_REMOVE", remove, TIMEOUT);
        verify(connection).send("PLATFORM_MODULE_ADMIN_SWAP", swap, TIMEOUT);
    }
}
