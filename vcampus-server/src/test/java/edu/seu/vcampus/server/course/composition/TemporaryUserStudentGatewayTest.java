package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.service.CourseStudentGateway;
import edu.seu.vcampus.server.course.service.StudentEnrollmentEligibility;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.user.service.UserQueryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporaryUserStudentGatewayTest {
    @Test
    void mapsAnActiveStudentUserIdToTheTemporaryStudentId() {
        UserQueryPort users = users(Map.of("user-1", identity("user-1", UserRole.STUDENT)));
        CourseStudentGateway gateway = TemporaryUserStudentGateway.create(users);

        assertThat(gateway.getEnrollmentEligibility("user-1"))
                .isEqualTo(new StudentEnrollmentEligibility("user-1", "ACTIVE"));
        assertThat(gateway.existsActiveStudent("user-1")).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TEACHER", "ADMIN"})
    void rejectsNonStudentRoles(UserRole role) {
        CourseStudentGateway gateway = TemporaryUserStudentGateway.create(
                users(Map.of("user-1", identity("user-1", role))));

        assertThatThrownBy(() -> gateway.getEnrollmentEligibility("user-1"))
                .isInstanceOf(StudentIneligibleException.class);
        assertThat(gateway.existsActiveStudent("user-1")).isFalse();
    }

    @Test
    void rejectsMissingAccounts() {
        CourseStudentGateway gateway = TemporaryUserStudentGateway.create(users(Map.of()));

        assertThatThrownBy(() -> gateway.getEnrollmentEligibility("missing"))
                .isInstanceOf(StudentIneligibleException.class);
        assertThat(gateway.existsActiveStudent("missing")).isFalse();
    }

    @Test
    void rejectsInactiveAccounts() {
        UserQueryPort users = users(Map.of(), Map.of("user-1", identity("user-1", UserRole.STUDENT)));
        CourseStudentGateway gateway = TemporaryUserStudentGateway.create(users);

        assertThatThrownBy(() -> gateway.getEnrollmentEligibility("user-1"))
                .isInstanceOf(StudentIneligibleException.class);
        assertThat(gateway.existsActiveStudent("user-1")).isFalse();
    }

    private static UserIdentity identity(String userId, UserRole role) {
        return new UserIdentity(userId, userId, role, Set.of(), false);
    }

    private static UserQueryPort users(Map<String, UserIdentity> active) {
        return users(active, Map.of());
    }

    private static UserQueryPort users(Map<String, UserIdentity> active,
                                       Map<String, UserIdentity> inactive) {
        return new UserQueryPort() {
            @Override public Optional<UserIdentity> findActiveUser(String userId) {
                return Optional.ofNullable(active.get(userId));
            }

            @Override public Optional<UserIdentity> findByUserId(String userId) {
                return Optional.ofNullable(active.getOrDefault(userId, inactive.get(userId)));
            }

            @Override public Optional<UserIdentity> findByLoginId(String loginId) {
                return Optional.empty();
            }

            @Override public boolean hasRole(String userId, UserRole role) {
                return Optional.ofNullable(active.get(userId)).map(UserIdentity::role).orElse(null) == role;
            }
        };
    }
}
