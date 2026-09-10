package edu.seu.vcampus.server.user.handler;

import edu.seu.vcampus.common.governance.AssignModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.ModuleAdministrationSnapshot;
import edu.seu.vcampus.common.governance.RemoveModuleAdministratorCommand;
import edu.seu.vcampus.common.governance.SwapModuleAdministratorsCommand;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.governance.ModuleAdministrationService;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.UserIdentity;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Boundary tests for the super-administrator governance commands. */
class ModuleAdministrationHandlersTest {
    private static final ClientContext CONTEXT =
            new ClientContext("connection", "127.0.0.1");

    @Test
    void superAdministratorCanListAssignRemoveAndSwap() {
        ModuleAdministrationService service = mock(ModuleAdministrationService.class);
        when(service.list()).thenReturn(new ModuleAdministrationSnapshot(List.of()));
        MessageRouter router = router(service, UserRole.SUPER_ADMIN);
        AssignModuleAdministratorCommand assign =
                new AssignModuleAdministratorCommand("COURSE", "first", 1);
        RemoveModuleAdministratorCommand remove =
                new RemoveModuleAdministratorCommand("COURSE", "first", 2);
        SwapModuleAdministratorsCommand swap = new SwapModuleAdministratorsCommand(
                "COURSE", "first", 2, "USER", "second", 3);

        assertThat(route(router, "PLATFORM_MODULE_ADMIN_LIST", EmptyRequest.INSTANCE).success())
                .isTrue();
        assertThat(route(router, "PLATFORM_MODULE_ADMIN_ASSIGN", assign).success()).isTrue();
        assertThat(route(router, "PLATFORM_MODULE_ADMIN_REMOVE", remove).success()).isTrue();
        assertThat(route(router, "PLATFORM_MODULE_ADMIN_SWAP", swap).success()).isTrue();
        verify(service).assign("actor", assign);
        verify(service).remove("actor", remove);
        verify(service).swap("actor", swap);
    }

    @Test
    void allOtherRolesAreRejectedEvenWhenPermissionCheckIsForgedToPass() {
        ModuleAdministrationService service = mock(ModuleAdministrationService.class);
        MessageRouter router = router(service, UserRole.USER_ADMIN);

        ResponseBody<?> response = route(router, "PLATFORM_MODULE_ADMIN_LIST",
                EmptyRequest.INSTANCE);

        assertThat(response.code()).isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void bodyValidationPrecedesAuthorization() {
        ModuleAdministrationService service = mock(ModuleAdministrationService.class);
        CountingAuthorization authorization = new CountingAuthorization(UserRole.SUPER_ADMIN);
        MessageRouter router = new MessageRouter(Map.of());
        new ModuleAdministrationHandlers(router, service, authorization, null);

        ResponseBody<?> response = route(router, "PLATFORM_MODULE_ADMIN_ASSIGN",
                EmptyRequest.INSTANCE);

        assertThat(response.code()).isEqualTo("COMMON_VALIDATION_FAILED");
        assertThat(authorization.calls).isZero();
    }

    @Test
    void lastAdministratorProtectionUsesStableCode() {
        ModuleAdministrationService service = mock(ModuleAdministrationService.class);
        RemoveModuleAdministratorCommand command =
                new RemoveModuleAdministratorCommand("SHOP", "last", 0);
        org.mockito.Mockito.doThrow(new IllegalStateException(
                "GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED"))
                .when(service).remove("actor", command);
        MessageRouter router = router(service, UserRole.SUPER_ADMIN);

        assertThat(route(router, "PLATFORM_MODULE_ADMIN_REMOVE", command).code())
                .isEqualTo("GOVERNANCE_LAST_MODULE_ADMIN_PROTECTED");
    }

    private static MessageRouter router(
            ModuleAdministrationService service, UserRole role) {
        MessageRouter router = new MessageRouter(Map.of());
        new ModuleAdministrationHandlers(router, service,
                new CountingAuthorization(role), null);
        return router;
    }

    private static ResponseBody<?> route(
            MessageRouter router, String command, Serializable body) {
        return router.route(new Message("request", MessageType.REQUEST, command,
                "token", body, 0), CONTEXT);
    }

    private static final class CountingAuthorization implements AuthorizationPort {
        private final UserRole role;
        private int calls;

        private CountingAuthorization(UserRole role) {
            this.role = role;
        }

        @Override public UserIdentity requireSession(String sessionToken) {
            calls++;
            return new UserIdentity("actor", "ACTOR", role, AccountStatus.ACTIVE);
        }

        @Override public void requirePermission(String sessionToken, String permissionCode) {
            calls++;
        }
    }
}
