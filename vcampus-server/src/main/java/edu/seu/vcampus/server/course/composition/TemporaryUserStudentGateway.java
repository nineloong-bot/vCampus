package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.course.domain.StudentIneligibleException;
import edu.seu.vcampus.server.course.service.CourseStudentGateway;
import edu.seu.vcampus.server.course.service.StudentEnrollmentEligibility;
import edu.seu.vcampus.server.user.service.UserQueryPort;

import java.util.Objects;

/** Temporary course adapter backed by active user identities. */
public final class TemporaryUserStudentGateway {
    private TemporaryUserStudentGateway() {
    }

    public static CourseStudentGateway create(UserQueryPort users) {
        return create(users, null, null);
    }

    /** Maps active students to a fixed curriculum identity for isolated demo runtimes. */
    public static CourseStudentGateway create(UserQueryPort users, String majorCode,
                                              Integer cohortYear) {
        Objects.requireNonNull(users, "users");
        return CourseStudentGateway.of(userId -> users.findActiveUser(userId)
                        .filter(identity -> identity.role() == UserRole.STUDENT)
                        .map(identity -> majorCode == null || cohortYear == null
                                ? new StudentEnrollmentEligibility(identity.userId(), "ACTIVE")
                                : new StudentEnrollmentEligibility(identity.userId(), "ACTIVE",
                                        majorCode, cohortYear))
                        .orElseThrow(StudentIneligibleException::new),
                studentId -> users.findActiveUser(studentId)
                        .map(identity -> identity.role() == UserRole.STUDENT)
                        .orElse(false));
    }
}
