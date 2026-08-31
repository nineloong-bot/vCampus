package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.SecurityAuditQuery;
import edu.seu.vcampus.common.user.SecurityAuditView;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.ForbiddenException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.service.SecurityAuditService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditHandlerTest {
    private static final ClientContext CONTEXT =
            new ClientContext("connection", "127.0.0.1");

    @Test
    void requiresAuditPermissionAndReturnsSanitizedPage() {
        AuthorizationPort authorization = mock(AuthorizationPort.class);
        SecurityAuditService audits = mock(SecurityAuditService.class);
        SecurityAuditQuery query = new SecurityAuditQuery(
                "user", "USER_LOGIN", "SUCCESS", null, null, 0, 20);
        SecurityAuditView view = new SecurityAuditView("audit", "actor",
                "USER_LOGIN", "USER", "target", "SUCCESS", LocalDateTime.MIN);
        when(authorization.requireSession("token")).thenReturn(new UserIdentity(
                "admin", "ADMIN", UserRole.ADMIN, AccountStatus.ACTIVE));
        when(audits.search(query)).thenReturn(new PageResult<>(List.of(view), 0, 20, 1));

        ResponseBody<?> response = new SecurityAuditHandler(authorization, audits)
                .handle(request(query), CONTEXT);

        verify(authorization).requirePermission("token", "USER_AUDIT_READ");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(new PageResult<>(List.of(view), 0, 20, 1));
        assertThat(response.toString()).doesNotContain("clientAddress", "password",
                "sessionToken");
    }

    @Test
    void rejectsNonAdministratorEvenWhenPermissionCheckSucceeds() {
        AuthorizationPort authorization = mock(AuthorizationPort.class);
        SecurityAuditService audits = mock(SecurityAuditService.class);
        SecurityAuditQuery query = new SecurityAuditQuery(
                null, null, null, null, null, 0, 20);
        when(authorization.requireSession("token")).thenReturn(new UserIdentity(
                "teacher", "TEACHER", UserRole.TEACHER, AccountStatus.ACTIVE));

        ResponseBody<?> response = new SecurityAuditHandler(authorization, audits)
                .handle(request(query), CONTEXT);

        assertThat(new Object[]{response.success(), response.code()})
                .containsExactly(false, "AUTH_FORBIDDEN");
        verify(audits, never()).search(query);
    }

    @Test
    void rejectsMalformedBodyBeforeAuthorization() {
        AuthorizationPort authorization = mock(AuthorizationPort.class);
        SecurityAuditService audits = mock(SecurityAuditService.class);

        ResponseBody<?> response = new SecurityAuditHandler(authorization, audits)
                .handle(request(EmptyRequest.INSTANCE), CONTEXT);

        verify(authorization, never()).requirePermission("token", "USER_AUDIT_READ");
        assertThat(new Object[]{response.success(), response.code()})
                .containsExactly(false, "COMMON_VALIDATION_FAILED");
    }

    @Test
    void permissionFailureUsesStableSafeResponse() {
        AuthorizationPort authorization = mock(AuthorizationPort.class);
        SecurityAuditService audits = mock(SecurityAuditService.class);
        SecurityAuditQuery query = new SecurityAuditQuery(
                null, null, null, null, null, 0, 20);
        org.mockito.Mockito.doThrow(new ForbiddenException())
                .when(authorization).requirePermission("token", "USER_AUDIT_READ");

        ResponseBody<?> response = new SecurityAuditHandler(authorization, audits)
                .handle(request(query), CONTEXT);

        assertThat(new Object[]{response.success(), response.code()})
                .containsExactly(false, "AUTH_FORBIDDEN");
        assertThat(response.toString()).doesNotContain("ForbiddenException", "token");
        verify(audits, never()).search(query);
    }

    private static Message request(java.io.Serializable body) {
        return new Message("request", MessageType.REQUEST, "SECURITY_AUDIT_SEARCH",
                "token", body, 0);
    }
}
