package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.common.course.CourseStudentCandidate;
import edu.seu.vcampus.common.course.CourseStudentCandidateQuery;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.StudentSearchQuery;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentSummary;
import edu.seu.vcampus.common.student.StudentView;
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

    /** Curriculum-aware adapter used once the student module exposes major and cohort. */
    public static <E> CourseStudentGateway students(
            Function<String, E> getEnrollmentEligibility,
            Function<E, String> studentId,
            Function<E, String> status,
            Function<E, String> majorCode,
            java.util.function.ToIntFunction<E> cohortYear,
            Predicate<String> activeStudentExists) {
        return students(getEnrollmentEligibility, number -> null, studentId, status,
                majorCode, cohortYear, activeStudentExists);
    }

    /** Curriculum-aware adapter with administrator lookup by student number. */
    public static <E> CourseStudentGateway students(
            Function<String, E> getEnrollmentEligibility,
            Function<String, E> getEligibilityByStudentNumber,
            Function<E, String> studentId,
            Function<E, String> status,
            Function<E, String> majorCode,
            java.util.function.ToIntFunction<E> cohortYear,
            Predicate<String> activeStudentExists) {
        Objects.requireNonNull(majorCode);
        Objects.requireNonNull(cohortYear);
        Objects.requireNonNull(getEligibilityByStudentNumber);
        Function<E, StudentEnrollmentEligibility> projection = eligibility -> eligibility == null ? null
                : new StudentEnrollmentEligibility(studentId.apply(eligibility), status.apply(eligibility),
                        majorCode.apply(eligibility), cohortYear.applyAsInt(eligibility));
        return CourseStudentGateway.of(
                userId -> projection.apply(Objects.requireNonNull(
                        getEnrollmentEligibility.apply(userId), "student eligibility")),
                activeStudentExists,
                number -> projection.apply(getEligibilityByStudentNumber.apply(number)));
    }

    /** Curriculum-aware adapter with student-number search and audit display lookup. */
    public static <E> CourseStudentGateway students(
            Function<String, E> getEnrollmentEligibility,
            Function<String, E> getEligibilityByStudentNumber,
            Function<E, String> studentId,
            Function<E, String> status,
            Function<E, String> majorCode,
            java.util.function.ToIntFunction<E> cohortYear,
            Predicate<String> activeStudentExists,
            Function<StudentSearchQuery, PageResult<StudentSummary>> searchStudents,
            Function<String, StudentView> getStudent) {
        CourseStudentGateway base = students(getEnrollmentEligibility, getEligibilityByStudentNumber,
                studentId, status, majorCode, cohortYear, activeStudentExists);
        Objects.requireNonNull(searchStudents);
        Objects.requireNonNull(getStudent);
        return new CourseStudentGateway() {
            @Override public StudentEnrollmentEligibility getEnrollmentEligibility(String userId) {
                return base.getEnrollmentEligibility(userId);
            }
            @Override public boolean existsActiveStudent(String id) { return base.existsActiveStudent(id); }
            @Override public StudentEnrollmentEligibility findActiveByStudentNumber(String number) {
                return base.findActiveByStudentNumber(number);
            }
            @Override public PageResult<CourseStudentCandidate> searchActiveStudents(
                    CourseStudentCandidateQuery query) {
                PageResult<StudentSummary> page = searchStudents.apply(new StudentSearchQuery(
                        query.studentNumber(), null, null, null, StudentStatus.ACTIVE,
                        query.page() + 1, query.pageSize()));
                var items = page.items().stream()
                        .filter(row -> query.studentNumber() == null || query.studentNumber().isBlank()
                                || row.studentNumber().contains(query.studentNumber()))
                        .map(row -> new CourseStudentCandidate(row.studentNumber(),
                                row.studentName(), row.className()))
                        .toList();
                return new PageResult<>(items, query.page(), query.pageSize(), items.size());
            }
            @Override public String findStudentNumber(String id) {
                return getStudent.apply(id).studentNumber();
            }
        };
    }
}
