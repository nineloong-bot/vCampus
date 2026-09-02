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
        Objects.requireNonNull(users, "users");
        return CourseStudentGateway.of(userId -> users.findActiveUser(userId)
                        .filter(identity -> identity.role() == UserRole.STUDENT)
                        .map(identity -> new StudentEnrollmentEligibility(identity.userId(), "ACTIVE"))
                        .orElseThrow(StudentIneligibleException::new),
                studentId -> users.findActiveUser(studentId)
                        .map(identity -> identity.role() == UserRole.STUDENT)
                        .orElse(false));
    }
}
