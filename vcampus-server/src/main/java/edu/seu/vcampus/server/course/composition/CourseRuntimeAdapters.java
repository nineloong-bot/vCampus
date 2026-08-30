package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.course.service.CourseAuthorizationGateway;
import edu.seu.vcampus.server.course.service.CourseSessionIdentity;
import edu.seu.vcampus.server.course.service.CourseStudentGateway;
import edu.seu.vcampus.server.course.service.StudentEnrollmentEligibility;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Type-safe adapters that keep course compilation independent until teammate modules are merged. */
public final class CourseRuntimeAdapters {
    private CourseRuntimeAdapters() { }

    /**
     * Adapts the user module's AuthorizationPort/UserIdentity/UserQueryPort without importing
     * their branch-owned types. The usable predicate must reject restricted first-password sessions.
     */
    public static <I> CourseAuthorizationGateway authorization(
            Function<String, I> requireSession,
            Function<I, String> userId,
            Function<I, String> role,
            Predicate<I> usable,
            BiPredicate<String, String> hasRole) {
        Objects.requireNonNull(requireSession);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(usable);
        Objects.requireNonNull(hasRole);
        return new CourseAuthorizationGateway() {
            @Override public CourseSessionIdentity requireSession(String sessionToken) {
                I identity = Objects.requireNonNull(requireSession.apply(sessionToken), "session identity");
                if (!usable.test(identity)) throw new InitialPasswordChangeRequiredException();
                return new CourseSessionIdentity(userId.apply(identity), role.apply(identity));
            }

            @Override public void requireUserRole(String assignedUserId, String expectedRole) {
                if (!hasRole.test(assignedUserId, expectedRole)) throw new CourseForbiddenException();
            }
        };
    }

    /** Adapts StudentQueryPort.getEnrollmentEligibility to the minimal course-owned projection. */
    public static <E> CourseStudentGateway students(
            Function<String, E> getEnrollmentEligibility,
            Function<E, String> studentId,
            Function<E, String> status,
            Predicate<String> activeStudentExists) {
        Objects.requireNonNull(getEnrollmentEligibility);
        Objects.requireNonNull(studentId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(activeStudentExists);
        return CourseStudentGateway.of(userId -> {
                    E eligibility = Objects.requireNonNull(
                            getEnrollmentEligibility.apply(userId), "student eligibility");
                    return new StudentEnrollmentEligibility(studentId.apply(eligibility), status.apply(eligibility));
                }, activeStudentExists);
    }
}
