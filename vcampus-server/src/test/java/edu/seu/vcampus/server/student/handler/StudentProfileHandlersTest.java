package edu.seu.vcampus.server.student.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.service.StudentProfileService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileHandlersTest {
    @Test
    void workspaceAlwaysUsesTheAuthenticatedStudentUserId() {
        AtomicReference<String> requestedUser = new AtomicReference<>();
        StudentProfileService profiles = new StubProfileService() {
            @Override public StudentProfileWorkspace getWorkspace(String userId) {
                requestedUser.set(userId);
                return null;
            }
        };
        MessageRouter router = router(profiles,
                new StudentPrincipal("student-user", Set.of("STUDENT"), Set.of()));

        var response = router.route(request("STUDENT_PROFILE_GET_WORKSPACE", EmptyRequest.INSTANCE),
                new ClientContext("connection", "local"));

        assertThat(response.success()).isTrue();
        assertThat(requestedUser).hasValue("student-user");
    }

    @Test
    void reviewListRequiresAdministratorPermission() {
        MessageRouter router = router(profileService(),
                new StudentPrincipal("teacher-user", Set.of("TEACHER"), Set.of()));

        var response = router.route(request("STUDENT_PROFILE_REVIEW_LIST",
                        new StudentProfileReviewQuery(1, 20)),
                new ClientContext("connection", "local"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("COMMON_FORBIDDEN");
    }

    @Test
    void allProfileCommandsAreRegisteredWhenProfileServiceIsPresent() {
        MessageRouter router = router(profileService(),
                new StudentPrincipal("admin-user", Set.of("ADMIN"), Set.of("STUDENT_WRITE")));

        assertThat(StudentHandlers.PROFILE_COMMANDS).allSatisfy(command ->
                assertThat(router.isRegistered(command)).isTrue());
    }

    @Test
    void administratorCanLoadACompleteProfileByStudentId() {
        AtomicReference<String> requestedStudent = new AtomicReference<>();
        StudentProfileService profiles = new StubProfileService() {
            @Override public StudentProfileData getProfileByStudentId(String studentId) {
                requestedStudent.set(studentId);
                return null;
            }
        };
        MessageRouter router = router(profiles,
                new StudentPrincipal("admin-user", Set.of("ADMIN"), Set.of("STUDENT_WRITE")));

        var response = router.route(request("STUDENT_GET_PROFILE", new EntityIdRequest("student-9")),
                new ClientContext("connection", "local"));

        assertThat(response.success()).isTrue();
        assertThat(requestedStudent).hasValue("student-9");
    }

    @Test
    void nonAdministratorCannotLoadAnotherStudentsCompleteProfile() {
        AtomicReference<String> requestedStudent = new AtomicReference<>();
        StudentProfileService profiles = new StubProfileService() {
            @Override public StudentProfileData getProfileByStudentId(String studentId) {
                requestedStudent.set(studentId);
                return null;
            }
        };
        MessageRouter router = router(profiles,
                new StudentPrincipal("student-user", Set.of("STUDENT"), Set.of()));

        var response = router.route(request("STUDENT_GET_PROFILE", new EntityIdRequest("student-9")),
                new ClientContext("connection", "local"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(requestedStudent).hasNullValue();
    }

    @Test
    void studentWritePermissionAloneDoesNotExposeCompleteProfiles() {
        AtomicReference<String> requestedStudent = new AtomicReference<>();
        StudentProfileService profiles = new StubProfileService() {
            @Override public StudentProfileData getProfileByStudentId(String studentId) {
                requestedStudent.set(studentId);
                return null;
            }
        };
        MessageRouter router = router(profiles,
                new StudentPrincipal("teacher-user", Set.of("TEACHER"), Set.of("STUDENT_WRITE")));

        var response = router.route(request("STUDENT_GET_PROFILE", new EntityIdRequest("student-9")),
                new ClientContext("connection", "local"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(requestedStudent).hasNullValue();
    }

    private static MessageRouter router(StudentProfileService profiles, StudentPrincipal principal) {
        MessageRouter router = new MessageRouter(Map.of());
        new StudentHandlers((command, context) -> null, StudentHandlerFixtures.studentService(),
                StudentHandlerFixtures.organizationQuery(), token -> principal,
                (request, actor, action) -> action.get(), profiles,
                (profile, generatedAt) -> null).register(router);
        return router;
    }

    private static StudentProfileService profileService() {
        return new StubProfileService();
    }

    private static class StubProfileService implements StudentProfileService {
        public StudentProfileWorkspace getWorkspace(String userId) { return null; }
        public StudentProfileData getProfileByStudentId(String studentId) { return null; }
        public StudentProfileWorkspace savePersonalDraft(String userId, SaveStudentPersonalDraftCommand c) { return null; }
        public StudentProfileWorkspace saveAttendanceDraft(String userId, SaveStudentAttendanceDraftCommand c) { return null; }
        public StudentProfileWorkspace submit(String userId, SubmitStudentProfileCommand c) { return null; }
        public PageResult<StudentProfileApplicationView> listPending(StudentProfileReviewQuery q) { return new PageResult<>(java.util.List.of(), 1, 20, 0); }
        public StudentProfileWorkspace getApplication(String id) { return null; }
        public StudentProfileApplicationView approve(String id, String reviewer, String comment) { return null; }
        public StudentProfileApplicationView reject(String id, String reviewer, String comment) { return null; }
    }

    private static Message request(String command, java.io.Serializable body) {
        return new Message("profile-request-1", MessageType.REQUEST, command, "token", body,
                System.currentTimeMillis());
    }

    private static final class StudentHandlerFixtures {
        private static edu.seu.vcampus.server.student.service.StudentService studentService() {
            return new edu.seu.vcampus.server.student.service.StudentService() {
                public StudentView getStudent(String id) { return null; }
                public StudentView getCurrentStudent(String id) { return null; }
                public PageResult<StudentSummary> searchStudents(StudentSearchQuery q) { return null; }
                public StudentView updateContact(UpdateStudentContactCommand c) { return null; }
                public StudentView updateEnrollment(UpdateStudentEnrollmentCommand c) { return null; }
                public StudentView changeStatus(ChangeStudentStatusCommand c) { return null; }
            };
        }
        private static edu.seu.vcampus.server.student.service.StudentOrganizationQuery organizationQuery() {
            return new edu.seu.vcampus.server.student.service.StudentOrganizationQuery() {
                public java.util.List<DepartmentView> listDepartments(boolean active) { return java.util.List.of(); }
                public java.util.List<MajorView> listMajors(String id) { return java.util.List.of(); }
                public java.util.List<ClassView> listClasses(String id) { return java.util.List.of(); }
            };
        }
    }
}
