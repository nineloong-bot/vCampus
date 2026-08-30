package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.student.domain.Department;
import edu.seu.vcampus.server.student.domain.Major;
import edu.seu.vcampus.server.student.domain.StudentClass;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.sql.Connection;
import java.util.Optional;

final class StudentFixtures {
    static void insertOrganization(Connection connection, AccessOrganizationRepository organizations) {
        organizations.insertDepartment(connection,
                new Department("department-1", "CS", "计算机学院", true, 0));
        organizations.insertMajor(connection,
                new Major("major-1", "department-1", "090", "计算机科学", true, 0));
        organizations.insertClass(connection,
                new StudentClass("class-1", "major-1", "090-24-1", "计科24-1", 2024, 1, true, 0));
    }

    static UserQueryPort userQueries(String userId, String loginId) {
        UserIdentity identity = new UserIdentity(
                userId, loginId, UserRole.STUDENT, AccountStatus.ACTIVE);
        return new UserQueryPort() {
            @Override public Optional<UserIdentity> findActiveUser(String candidateUserId) {
                return findByUserId(candidateUserId);
            }

            @Override public Optional<UserIdentity> findByUserId(String candidateUserId) {
                return userId.equals(candidateUserId) ? Optional.of(identity) : Optional.empty();
            }

            @Override public Optional<UserIdentity> findByLoginId(String candidateLoginId) {
                return loginId.equals(candidateLoginId) ? Optional.of(identity) : Optional.empty();
            }

            @Override public boolean hasRole(String candidateUserId, UserRole role) {
                return userId.equals(candidateUserId) && role == UserRole.STUDENT;
            }
        };
    }

    private StudentFixtures() { }
}
