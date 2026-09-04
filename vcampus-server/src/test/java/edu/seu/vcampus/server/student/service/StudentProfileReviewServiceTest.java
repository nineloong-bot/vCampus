package edu.seu.vcampus.server.student.service;

import edu.seu.vcampus.common.student.AttendanceMode;
import edu.seu.vcampus.common.student.SaveStudentAttendanceDraftCommand;
import edu.seu.vcampus.common.student.SaveStudentPersonalDraftCommand;
import edu.seu.vcampus.common.student.StudentPersonalProfile;
import edu.seu.vcampus.common.student.StudentProfileApplicationStatus;
import edu.seu.vcampus.common.student.SubmitStudentProfileCommand;
import edu.seu.vcampus.common.student.WithdrawStudentProfileCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.student.repository.AccessOrganizationRepository;
import edu.seu.vcampus.server.student.repository.StudentChangeRepository;
import edu.seu.vcampus.server.student.repository.StudentProfileApplicationRepository;
import edu.seu.vcampus.server.student.repository.StudentRepository;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentProfileReviewServiceTest {
    private StudentAccessTestDatabase database;
    private StudentProfileService service;

    @BeforeEach
    void setUp() throws Exception {
        database = new StudentAccessTestDatabase();
        database.transactions().inTransaction(connection -> {
            StudentFixtures.insertOrganization(connection, new AccessOrganizationRepository());
            new StudentRepository().insert(connection,
                    StudentProfileUpdateTest.student(edu.seu.vcampus.common.student.StudentStatus.ACTIVE));
            try (var statement = connection.prepareStatement(
                    "UPDATE tblStudent SET attendanceMode='RESIDENT', enrolled=TRUE, onCampus=TRUE, "
                            + "campus='九龙湖校区', educationLevel='本科', trainingMode='非定向', "
                            + "programLengthYears=4 WHERE studentId='student-1'")) {
                statement.executeUpdate();
            }
            return null;
        });
        service = new StudentProfileServiceImpl(database.transactions(),
                new StripedResourceLockManager(), new StudentRepository(),
                new StudentProfileApplicationRepository(), new StudentChangeRepository(),
                StudentFixtures.userQueries("user-1", "213240001"));
    }

    @Test
    void draftDoesNotChangeFormalProfileUntilAdministratorApproves() throws Exception {
        var draft = service.saveAttendanceDraft("user-1",
                new SaveStudentAttendanceDraftCommand(AttendanceMode.DAY_STUDENT, 0));

        assertThat(draft.formalProfile().academic().attendanceMode())
                .isEqualTo(AttendanceMode.RESIDENT);
        assertThat(draft.application().attendanceMode()).isEqualTo(AttendanceMode.DAY_STUDENT);
        assertThat(draft.application().status()).isEqualTo(StudentProfileApplicationStatus.DRAFT);
        assertThat(database.stringValue("SELECT attendanceMode FROM tblStudent WHERE studentId='student-1'"))
                .isEqualTo("RESIDENT");

        var pending = service.submit("user-1",
                new SubmitStudentProfileCommand(draft.application().applicationVersion()));
        service.approve(pending.application().applicationId(), "admin-1", "核验通过");

        assertThat(service.getWorkspace("user-1").formalProfile().academic().attendanceMode())
                .isEqualTo(AttendanceMode.DAY_STUDENT);
        assertThat(database.count("tblStudentChange")).isEqualTo(1);
    }

    @Test
    void pendingApplicationIsImmutableAndRejectionLeavesFormalDataUntouched() {
        var draft = service.saveAttendanceDraft("user-1",
                new SaveStudentAttendanceDraftCommand(AttendanceMode.OTHER, 0));
        var pending = service.submit("user-1",
                new SubmitStudentProfileCommand(draft.application().applicationVersion()));

        assertThatThrownBy(() -> service.saveAttendanceDraft("user-1",
                new SaveStudentAttendanceDraftCommand(AttendanceMode.LODGING,
                        pending.application().applicationVersion())))
                .isInstanceOf(StudentProfileApplicationException.class)
                .hasMessageContaining("正在审核");

        service.reject(pending.application().applicationId(), "admin-1", "证明材料不完整");
        var rejected = service.getWorkspace("user-1");
        assertThat(rejected.formalProfile().academic().attendanceMode())
                .isEqualTo(AttendanceMode.RESIDENT);
        assertThat(rejected.application().status())
                .isEqualTo(StudentProfileApplicationStatus.REJECTED);
        assertThat(rejected.application().reviewComment()).isEqualTo("证明材料不完整");
    }

    @Test
    void rejectionRequiresAReason() {
        var draft = service.saveAttendanceDraft("user-1",
                new SaveStudentAttendanceDraftCommand(AttendanceMode.OTHER, 0));
        var pending = service.submit("user-1",
                new SubmitStudentProfileCommand(draft.application().applicationVersion()));

        assertThatThrownBy(() -> service.reject(
                pending.application().applicationId(), "admin-1", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("驳回原因");
    }

    @Test
    void invalidPersonalDraftIsRejectedAtTheServiceBoundary() throws Exception {
        var invalid = new StudentPersonalProfile(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                false, null, false, null, null, null, null, 99, null, null,
                false, null, null);

        assertThatThrownBy(() -> service.savePersonalDraft("user-1",
                new SaveStudentPersonalDraftCommand(invalid, 0)))
                .isInstanceOf(StudentProfileApplicationException.class)
                .extracting(error -> ((StudentProfileApplicationException) error).code())
                .isEqualTo("STUDENT_PROFILE_FIELD_INVALID");
        assertThat(database.count("tblStudentProfileApplication")).isZero();
    }

    @Test
    void personalDraftRejectsBirthDateThatWouldMakeStudentUnderageAtEnrollment() throws Exception {
        var invalid = new StudentPersonalProfile(null, null, null, null, null, null, null,
                null, java.time.LocalDate.of(2007, 9, 2), null, null, null, null, null, null,
                null, null, null, false, null, false, null, null, null, null, null, null,
                null, false, null, null);

        assertThatThrownBy(() -> service.savePersonalDraft("user-1",
                new SaveStudentPersonalDraftCommand(invalid, 0)))
                .isInstanceOf(StudentProfileApplicationException.class)
                .hasMessageContaining("入学时必须已年满 18 周岁");
        assertThat(database.count("tblStudentProfileApplication")).isZero();
    }

    @Test
    void withdrawalReturnsPendingSnapshotToEditableDraftWithoutLosingChanges() {
        var draft = service.saveAttendanceDraft("user-1",
                new SaveStudentAttendanceDraftCommand(AttendanceMode.DAY_STUDENT, 0));
        var pending = service.submit("user-1",
                new SubmitStudentProfileCommand(draft.application().applicationVersion()));

        var withdrawn = service.withdraw("user-1",
                new WithdrawStudentProfileCommand(pending.application().applicationVersion()));

        assertThat(withdrawn.application().status()).isEqualTo(StudentProfileApplicationStatus.DRAFT);
        assertThat(withdrawn.application().attendanceMode()).isEqualTo(AttendanceMode.DAY_STUDENT);
        assertThat(withdrawn.application().personal()).isEqualTo(pending.application().personal());
        assertThat(withdrawn.application().submittedAt()).isNull();
        assertThat(withdrawn.application().applicationVersion())
                .isEqualTo(pending.application().applicationVersion() + 1);
        var edited = service.saveAttendanceDraft("user-1", new SaveStudentAttendanceDraftCommand(
                AttendanceMode.LODGING, withdrawn.application().applicationVersion()));
        assertThat(edited.application().attendanceMode()).isEqualTo(AttendanceMode.LODGING);
    }

    @Test
    void completedAdminReviewCannotBeWithdrawn() {
        var draft = service.saveAttendanceDraft("user-1",
                new SaveStudentAttendanceDraftCommand(AttendanceMode.DAY_STUDENT, 0));
        var pending = service.submit("user-1",
                new SubmitStudentProfileCommand(draft.application().applicationVersion()));
        service.approve(pending.application().applicationId(), "admin-1", "通过");

        assertThatThrownBy(() -> service.withdraw("user-1",
                new WithdrawStudentProfileCommand(pending.application().applicationVersion())))
                .isInstanceOf(StudentProfileApplicationException.class)
                .extracting(error -> ((StudentProfileApplicationException) error).code())
                .isEqualTo("STUDENT_PROFILE_NOT_PENDING");
    }
}
