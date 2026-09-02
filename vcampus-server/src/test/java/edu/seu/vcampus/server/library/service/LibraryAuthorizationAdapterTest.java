package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.UserIdentity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LibraryAuthorizationAdapterTest {
    @Test
    void derivesBorrowerIdentityFromTheAuthenticatedSession() {
        AuthorizationPort authorization = new AuthorizationPort() {
            @Override public UserIdentity requireSession(String sessionToken) {
                assertThat(sessionToken).isEqualTo("session-1");
                return new UserIdentity("user-1", "20260001", UserRole.TEACHER,
                        AccountStatus.ACTIVE);
            }

            @Override public void requirePermission(String sessionToken, String permissionCode) {
                throw new AssertionError("permission check not expected");
            }
        };

        BorrowerIdentity borrower = new LibraryAuthorizationAdapter(authorization)
                .requireBorrower("session-1");

        assertThat(borrower).isEqualTo(new BorrowerIdentity("user-1", "TEACHER"));
    }
}
