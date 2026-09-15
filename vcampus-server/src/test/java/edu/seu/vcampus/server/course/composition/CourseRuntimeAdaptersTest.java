package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.common.course.CourseStudentCandidateQuery;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.student.StudentStatus;
import edu.seu.vcampus.common.student.StudentSummary;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.StudentView;
import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
import edu.seu.vcampus.server.security.InitialPasswordChangeRequiredException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseRuntimeAdaptersTest {
    @Test
    void bindsUserSessionRestrictionAndAssignedTeacherRole() {
        record ExternalIdentity(String userId, String role, boolean restricted) { }
        var adapter = CourseRuntimeAdapters.authorization(
                token -> new ExternalIdentity(token, "STUDENT", "restricted".equals(token)),
                ExternalIdentity::userId, ExternalIdentity::role, identity -> !identity.restricted(),
                (userId, expectedRole) -> "teacher-1".equals(userId) && "TEACHER".equals(expectedRole));

        assertThat(adapter.requireSession("student-1").userId()).isEqualTo("student-1");
        assertThat(adapter.requireSession("student-1").role()).isEqualTo("STUDENT");
        assertThatThrownBy(() -> adapter.requireSession("restricted"))
                .isInstanceOf(InitialPasswordChangeRequiredException.class);
        adapter.requireUserRole("teacher-1", "TEACHER");
        assertThatThrownBy(() -> adapter.requireUserRole("student-1", "TEACHER"))
                .isInstanceOf(CourseForbiddenException.class);
    }

    @Test
    void bindsStudentQueryEligibilityWithoutLeakingStudentTypes() {
        record ExternalEligibility(String studentId, String status) { }
        var adapter = CourseRuntimeAdapters.students(
                userId -> new ExternalEligibility("student-for-" + userId, "ACTIVE"),
                ExternalEligibility::studentId, ExternalEligibility::status,
                studentId -> "student-for-user-1".equals(studentId));

        assertThat(adapter.getEnrollmentEligibility("user-1").studentId()).isEqualTo("student-for-user-1");
        assertThat(adapter.getEnrollmentEligibility("user-1").status()).isEqualTo("ACTIVE");
        assertThat(adapter.existsActiveStudent("student-for-user-1")).isTrue();
        assertThat(adapter.existsActiveStudent("missing")).isFalse();
    }

    @Test
    void bindsCurriculumContextFromStudentEligibility() {
        record ExternalEligibility(String studentId, String status,
                                   String majorCode, int cohortYear) { }
        var adapter = CourseRuntimeAdapters.students(
                userId -> new ExternalEligibility("student-1", "ACTIVE", "090", 2023),
                ExternalEligibility::studentId, ExternalEligibility::status,
                ExternalEligibility::majorCode, ExternalEligibility::cohortYear,
                studentId -> true);

        var eligibility = adapter.getEnrollmentEligibility("user-1");
        assertThat(eligibility.majorCode()).isEqualTo("090");
        assertThat(eligibility.cohortYear()).isEqualTo(2023);
        assertThat(eligibility.hasCurriculumContext()).isTrue();
    }

    @Test
    void mapsStudentNumberSearchAndAuditIdentityAcrossTheCourseBoundary() {
        record ExternalEligibility(String studentId, String status,
                                   String majorCode, int cohortYear) { }
        StudentSummary summary = new StudentSummary("student-1", "213260001", "213260001",
                "赵明轩", "major-1", "class-1", StudentStatus.ACTIVE, "软件工程一班");
        StudentView view = new StudentView("student-1", "user-1", "213260001", "213260001",
                StudentType.UNDERGRADUATE, "赵明轩", "男", null, null, "major-1", "class-1",
                java.time.LocalDate.of(2023, 9, 1), StudentStatus.ACTIVE, 0,
                "计算机学院", "软件工程", "软件工程一班");
        var adapter = CourseRuntimeAdapters.students(
                ignored -> new ExternalEligibility("student-1", "ACTIVE", "090", 2023),
                ignored -> new ExternalEligibility("student-1", "ACTIVE", "090", 2023),
                ExternalEligibility::studentId, ExternalEligibility::status,
                ExternalEligibility::majorCode, ExternalEligibility::cohortYear,
                ignored -> true,
                query -> new PageResult<>(java.util.List.of(summary), query.page(), query.pageSize(), 1),
                ignored -> view);

        assertThat(adapter.searchActiveStudents(new CourseStudentCandidateQuery("21326", 0, 20)).items())
                .singleElement().satisfies(candidate -> {
                    assertThat(candidate.studentNumber()).isEqualTo("213260001");
                    assertThat(candidate.studentName()).isEqualTo("赵明轩");
                    assertThat(candidate.className()).isEqualTo("软件工程一班");
                });
        assertThat(adapter.findStudentNumber("student-1")).isEqualTo("213260001");
    }
}
