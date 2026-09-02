package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.service.StudentAdmissionService;
import edu.seu.vcampus.server.student.service.StudentService;
import edu.seu.vcampus.server.student.service.StudentOrganizationQuery;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StudentHandlersTest {
    @Test
    void studentCreateRejectsUserWithoutWritePermissionBeforeBusinessCall() {
        var router = new MessageRouter(Map.of());
        StudentHandlers handlers = new StudentHandlers((command, context) -> null,
                studentService(), organizationQuery(),
                token -> new StudentPrincipal("user-1", Set.of("STUDENT"), Set.of()));
        handlers.register(router);
        var command = new CreateStudentAdmissionCommand("张三", "MALE", null, null,
                "major-1", "class-1", 2024, StudentType.UNDERGRADUATE);

        var response = router.route(request("STUDENT_CREATE", command), client());

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("COMMON_FORBIDDEN");
    }

    @Test
    void allStudentCommandsIncludingAdministrationAreRegistered() {
        var router = new MessageRouter(Map.of());
        new StudentHandlers((command, context) -> null, studentService(), organizationQuery(),
                token -> new StudentPrincipal("admin-1", Set.of("ADMIN"), Set.of("STUDENT_WRITE")))
                .register(router);

        assertThat(StudentHandlers.COMMANDS).allSatisfy(command ->
                assertThat(router.isRegistered(command)).isTrue());
        assertThat(StudentHandlers.COMMANDS).contains(
                "STUDENT_GET_CHANGES", "STUDENT_SAVE_DEPARTMENT",
                "STUDENT_SAVE_MAJOR", "STUDENT_SAVE_CLASS");
    }

    @Test
    void concurrentProfileUpdateReturnsStableProtocolError() {
        var router = new MessageRouter(Map.of());
        StudentService failing = new StudentService() {
            public edu.seu.vcampus.common.student.StudentView getStudent(String id) {
                return new edu.seu.vcampus.common.student.StudentView("student-1", "user-1",
                        "213240001", "09024101", StudentType.UNDERGRADUATE, "张三", "MALE",
                        null, null, "major-1", "class-1", java.time.LocalDate.of(2024, 9, 1),
                        edu.seu.vcampus.common.student.StudentStatus.ACTIVE, 1,
                        "计算机学院", "软件工程", "软工2401");
            }
            public edu.seu.vcampus.common.student.StudentView getCurrentStudent(String id) { return null; }
            public edu.seu.vcampus.common.paging.PageResult<edu.seu.vcampus.common.student.StudentSummary> searchStudents(edu.seu.vcampus.common.student.StudentSearchQuery q) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateContact(edu.seu.vcampus.common.student.UpdateStudentContactCommand c) { throw new ConcurrentModificationException(); }
            public edu.seu.vcampus.common.student.StudentView updateEnrollment(edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView changeStatus(edu.seu.vcampus.common.student.ChangeStudentStatusCommand c) { return null; }
        };
        new StudentHandlers((command, context) -> null, failing, organizationQuery(),
                token -> new StudentPrincipal("user-1", Set.of("STUDENT"), Set.of())).register(router);

        var response = router.route(request("STUDENT_UPDATE_CONTACT",
                new edu.seu.vcampus.common.student.UpdateStudentContactCommand(
                        "student-1", "new@seu.edu.cn", null, 0)), client());

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("COMMON_CONCURRENT_MODIFICATION");
    }

    @Test
    void authenticatedAdminIdentityReachesStatusAuditBoundary() {
        var operator = new AtomicReference<String>();
        StudentService recording = new StudentService() {
            public edu.seu.vcampus.common.student.StudentView getStudent(String id) { return null; }
            public edu.seu.vcampus.common.student.StudentView getCurrentStudent(String id) { return null; }
            public edu.seu.vcampus.common.paging.PageResult<edu.seu.vcampus.common.student.StudentSummary> searchStudents(edu.seu.vcampus.common.student.StudentSearchQuery q) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateContact(edu.seu.vcampus.common.student.UpdateStudentContactCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateEnrollment(edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView changeStatus(edu.seu.vcampus.common.student.ChangeStudentStatusCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView changeStatus(edu.seu.vcampus.common.student.ChangeStudentStatusCommand c, String userId) { operator.set(userId); return null; }
        };
        var router = new MessageRouter(Map.of());
        new StudentHandlers((command, context) -> null, recording, organizationQuery(),
                token -> new StudentPrincipal("admin-42", Set.of("ADMIN"), Set.of())).register(router);

        router.route(request("STUDENT_CHANGE_STATUS", new edu.seu.vcampus.common.student.ChangeStudentStatusCommand(
                "student-1", edu.seu.vcampus.common.student.StudentStatus.SUSPENDED,
                java.time.LocalDate.now(), "休学", 0)), client());

        assertThat(operator).hasValue("admin-42");
    }

    @Test
    void teacherDetailResponseKeepsContactFields() {
        var router = new MessageRouter(Map.of());
        StudentService profiles = studentServiceWithProfile();
        new StudentHandlers((command, context) -> null, profiles, organizationQuery(),
                token -> new StudentPrincipal("teacher-1", Set.of("TEACHER"), Set.of())).register(router);

        var response = router.route(request("STUDENT_GET",
                new edu.seu.vcampus.common.student.EntityIdRequest("student-1")), client());
        var view = (edu.seu.vcampus.common.student.StudentView) response.data();

        assertThat(view.email()).isEqualTo("private@seu.edu.cn");
        assertThat(view.phone()).isEqualTo("13800000000");
        assertThat(view.studentNumber()).isEqualTo("09024101");
    }

    private static Message request(String command, java.io.Serializable body) {
        return new Message("8e7c1a21-9d44-4c82-978b-df34326a0341", MessageType.REQUEST,
                command, "token", body, System.currentTimeMillis());
    }
    private static ClientContext client() { return new ClientContext("connection-1", "local"); }

    private static StudentService studentService() {
        return new StudentService() {
            public edu.seu.vcampus.common.student.StudentView getStudent(String id) { return null; }
            public edu.seu.vcampus.common.student.StudentView getCurrentStudent(String id) { return null; }
            public edu.seu.vcampus.common.paging.PageResult<edu.seu.vcampus.common.student.StudentSummary> searchStudents(edu.seu.vcampus.common.student.StudentSearchQuery q) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateContact(edu.seu.vcampus.common.student.UpdateStudentContactCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateEnrollment(edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView changeStatus(edu.seu.vcampus.common.student.ChangeStudentStatusCommand c) { return null; }
        };
    }

    private static StudentService studentServiceWithProfile() {
        return new StudentService() {
            public edu.seu.vcampus.common.student.StudentView getStudent(String id) {
                return new edu.seu.vcampus.common.student.StudentView("student-1", "user-1",
                        "213240001", "09024101", StudentType.UNDERGRADUATE, "张三", "MALE",
                        "private@seu.edu.cn", "13800000000", "major-1", "class-1",
                        java.time.LocalDate.of(2024, 9, 1),
                        edu.seu.vcampus.common.student.StudentStatus.ACTIVE, 1,
                        "计算机学院", "软件工程", "软工2401");
            }
            public edu.seu.vcampus.common.student.StudentView getCurrentStudent(String id) { return getStudent(id); }
            public edu.seu.vcampus.common.paging.PageResult<edu.seu.vcampus.common.student.StudentSummary> searchStudents(edu.seu.vcampus.common.student.StudentSearchQuery q) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateContact(edu.seu.vcampus.common.student.UpdateStudentContactCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView updateEnrollment(edu.seu.vcampus.common.student.UpdateStudentEnrollmentCommand c) { return null; }
            public edu.seu.vcampus.common.student.StudentView changeStatus(edu.seu.vcampus.common.student.ChangeStudentStatusCommand c) { return null; }
        };
    }

    private static StudentOrganizationQuery organizationQuery() {
        return new StudentOrganizationQuery() {
            public java.util.List<edu.seu.vcampus.common.student.DepartmentView> listDepartments(boolean active) { return java.util.List.of(); }
            public java.util.List<edu.seu.vcampus.common.student.MajorView> listMajors(String id) { return java.util.List.of(); }
            public java.util.List<edu.seu.vcampus.common.student.ClassView> listClasses(String id) { return java.util.List.of(); }
        };
    }
}
