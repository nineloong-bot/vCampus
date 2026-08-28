package edu.seu.vcampus.server.course.composition;

import edu.seu.vcampus.server.course.domain.CourseForbiddenException;
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
        assertThatThrownBy(() -> adapter.requireSession("restricted")).isInstanceOf(CourseForbiddenException.class);
        adapter.requireUserRole("teacher-1", "TEACHER");
        assertThatThrownBy(() -> adapter.requireUserRole("student-1", "TEACHER"))
                .isInstanceOf(CourseForbiddenException.class);
    }

    @Test
    void bindsStudentQueryEligibilityWithoutLeakingStudentTypes() {
        record ExternalEligibility(String studentId, String status) { }
        var adapter = CourseRuntimeAdapters.students(
                userId -> new ExternalEligibility("student-for-" + userId, "ACTIVE"),
                ExternalEligibility::studentId, ExternalEligibility::status);

        assertThat(adapter.getEnrollmentEligibility("user-1").studentId()).isEqualTo("student-for-user-1");
        assertThat(adapter.getEnrollmentEligibility("user-1").status()).isEqualTo("ACTIVE");
    }
}
