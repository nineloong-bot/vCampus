package edu.seu.vcampus.server.shop.adapter;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.SessionExpiredException;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static edu.seu.vcampus.common.user.AccountStatus.ACTIVE;
import static edu.seu.vcampus.common.user.AccountStatus.DISABLED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FoundationShopUserAdapterTest {
    private final AuthorizationPort authorization = mock(AuthorizationPort.class);
    private final Predicate<String> restrictedSessions = "restricted-token"::equals;
    private final FoundationShopUserAdapter adapter =
            new FoundationShopUserAdapter(authorization, restrictedSessions);

    @Test
    void mapsStudentAndRejectsRestrictedSession() {
        when(authorization.requireSession("student-token"))
                .thenReturn(new UserIdentity("buyer-1", "DEMO_BUYER",
                        UserRole.STUDENT, ACTIVE));

        assertThat(adapter.requireUser("student-token"))
                .isEqualTo(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));

        when(authorization.requireSession("restricted-token"))
                .thenReturn(new UserIdentity("buyer-2", "FIRST_LOGIN",
                        UserRole.STUDENT, ACTIVE));

        assertThatThrownBy(() -> adapter.requireUser("restricted-token"))
                .isInstanceOf(ShopAccessException.class)
                .hasMessage("AUTH_INITIAL_PASSWORD_CHANGE_REQUIRED");
    }

    @Test
    void mapsTeacherAndAdministratorRoles() {
        when(authorization.requireSession("teacher-token"))
                .thenReturn(new UserIdentity("teacher-1", "DEMO_TEACHER",
                        UserRole.TEACHER, ACTIVE));
        when(authorization.requireSession("administrator-token"))
                .thenReturn(new UserIdentity("admin-1", "DEMO_ADMIN",
                        UserRole.ADMIN, ACTIVE));

        assertThat(adapter.requireUser("teacher-token"))
                .isEqualTo(new ShopUser("teacher-1", ShopUserKind.TEACHER, true));
        assertThat(adapter.requireUser("administrator-token"))
                .isEqualTo(new ShopUser("admin-1", ShopUserKind.ADMINISTRATOR, true));
    }

    @Test
    void mapsDisabledAccountAsInactiveShopUser() {
        when(authorization.requireSession("disabled-token"))
                .thenReturn(new UserIdentity("buyer-3", "DISABLED_BUYER",
                        UserRole.STUDENT, DISABLED));

        assertThat(adapter.requireUser("disabled-token"))
                .isEqualTo(new ShopUser("buyer-3", ShopUserKind.STUDENT, false));
    }

    @Test
    void mapsExpiredSessionToStableShopCode() {
        when(authorization.requireSession("expired-token"))
                .thenThrow(new SessionExpiredException());

        assertThatThrownBy(() -> adapter.requireUser("expired-token"))
                .isInstanceOf(ShopAccessException.class)
                .hasMessage("AUTH_SESSION_EXPIRED");
    }

    @Test
    void rejectsAdministratorAccessUntilAnAdministratorContextExists() {
        assertThatThrownBy(adapter::requireAdministrator)
                .isInstanceOf(ShopAccessException.class)
                .hasMessage("AUTH_FORBIDDEN");
    }
}
