package edu.seu.vcampus.server.security;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.server.session.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationServiceTest {
    @Test
    void restrictedSessionRejectsBusinessPermissionButAllowsSessionLookup() {
        SessionRegistry sessions = new SessionRegistry();
        String token = sessions.create(new UserIdentity("student", "213242478", UserRole.STUDENT,
                AccountStatus.ACTIVE), Set.of(), true, "client-a");
        AuthorizationPort authorization = new AuthorizationService(sessions);

        authorization.requireSession(token);

        assertThatThrownBy(() -> authorization.requirePermission(token, "COURSE_READ"))
                .isInstanceOf(InitialPasswordChangeRequiredException.class);
    }

    @Test
    void normalSessionRequiresTheGrantedPermission() {
        SessionRegistry sessions = new SessionRegistry();
        AuthorizationPort authorization = new AuthorizationService(sessions);
        String administrator = sessions.create(new UserIdentity("admin", "ADMIN", UserRole.ADMIN,
                AccountStatus.ACTIVE), Set.of("USER_READ_ALL"), false, "client-a");
        String student = sessions.create(new UserIdentity("student", "STUDENT", UserRole.STUDENT,
                AccountStatus.ACTIVE), Set.of(), false, "client-b");

        assertThatCode(() -> authorization.requirePermission(administrator, "USER_READ_ALL"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requirePermission(student, "USER_READ_ALL"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void expiredOrRevokedSessionCannotBeUsed() {
        MutableClock clock = new MutableClock();
        SessionRegistry sessions = new SessionRegistry(clock, Duration.ofMinutes(1));
        String token = sessions.create(new UserIdentity("student", "STUDENT", UserRole.STUDENT,
                AccountStatus.ACTIVE), Set.of(), false, "client-a");
        clock.advance(Duration.ofMinutes(1));

        assertThatThrownBy(() -> sessions.requireSession(token))
                .isInstanceOf(SessionExpiredException.class);
        String liveToken = sessions.create(new UserIdentity("student", "STUDENT", UserRole.STUDENT,
                AccountStatus.ACTIVE), Set.of(), false, "client-a");
        sessions.revokeAllForUser("student");
        assertThatThrownBy(() -> sessions.requireSession(liveToken))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void missingTokenUsesTheSafeExpiredSessionError() {
        AuthorizationPort authorization = new AuthorizationService(new SessionRegistry());

        assertThatThrownBy(() -> authorization.requireSession(null))
                .isInstanceOf(SessionExpiredException.class);
    }

    @Test
    void sessionSnapshotOwnsPermissionsRestrictionAndClientInstance() {
        SessionRegistry sessions = new SessionRegistry();
        UserIdentity identity = new UserIdentity("admin", "ADMIN", UserRole.ADMIN,
                AccountStatus.ACTIVE);
        String token = sessions.create(identity, Set.of("USER_AUDIT_READ"), true, "client-42");

        SessionRegistry.SessionSnapshot snapshot = sessions.requireSnapshot(token);

        assertThat(snapshot.identity()).isEqualTo(identity);
        assertThat(snapshot.permissions()).containsExactly("USER_AUDIT_READ");
        assertThat(snapshot.restricted()).isTrue();
        assertThat(snapshot.clientInstanceId()).isEqualTo("client-42");
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
    }
}
