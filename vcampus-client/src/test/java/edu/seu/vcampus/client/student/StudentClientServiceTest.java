package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.SaveDepartmentCommand;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class StudentClientServiceTest {
    @Test
    void studentRequestsNeverInvokeSocketClientOnEdt() throws Exception {
        var sendThreadIsEdt = new AtomicBoolean(true);
        var sendEntered = new CountDownLatch(1);
        StudentRequestClient client = new StudentRequestClient() {
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                sendThreadIsEdt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                sendEntered.countDown();
                return CompletableFuture.completedFuture(ResponseBody.success(null));
            }
        };
        var service = new StudentClientService(client, Duration.ofSeconds(3));

        javax.swing.SwingUtilities.invokeAndWait(service::getCurrent);

        assertThat(sendEntered.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(sendThreadIsEdt).isFalse();
    }

    @Test
    void admissionUsesStudentCreateCommandAndPreservesTypedResponse() {
        var client = new RecordingClient();
        var service = new StudentClientService(client, Duration.ofSeconds(3));
        var command = new CreateStudentAdmissionCommand("张三", "MALE", null, null,
                "major-1", "class-1", 2024, StudentType.UNDERGRADUATE);

        ResponseBody<StudentAdmissionResult> response = service.admit(command).join();

        assertThat(client.command).isEqualTo("STUDENT_CREATE");
        assertThat(client.body).isSameAs(command);
        assertThat(client.timeout).isEqualTo(Duration.ofSeconds(3));
        assertThat(response.success()).isTrue();
    }

    @Test
    void manualCreationUsesSeparateCommand() {
        var client = new RecordingClient();
        var service = new StudentClientService(client, Duration.ofSeconds(3));
        var command = new CreateStudentManualCommand("213240099", "09024199", "李雷", "男",
                StudentType.UNDERGRADUATE, "居民身份证", "110105200009030011",
                java.time.LocalDate.of(2000, 9, 3), java.time.LocalDate.of(2024, 9, 1), "class-1");

        service.createManual(command).join();

        assertThat(client.command).isEqualTo("STUDENT_CREATE_MANUAL");
        assertThat(client.body).isSameAs(command);
    }

    @Test
    void organizationSaveUsesAdministrativeMessageContract() {
        var client = new RecordingClient();
        var service = new StudentClientService(client, Duration.ofSeconds(3));
        var command = new SaveDepartmentCommand(null, "CS", "计算机学院", true, 0);

        service.saveDepartment(command).join();

        assertThat(client.command).isEqualTo("STUDENT_SAVE_DEPARTMENT");
        assertThat(client.body).isSameAs(command);
    }

    @Test
    void profileDraftReviewAndExportUseDedicatedMessageContracts() {
        var client = new RecordingClient();
        var service = new StudentClientService(client, Duration.ofSeconds(3));

        service.getProfileWorkspace().join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_GET_WORKSPACE");
        service.saveAttendanceDraft(new SaveStudentAttendanceDraftCommand(AttendanceMode.RESIDENT, 3)).join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_SAVE_ATTENDANCE_DRAFT");
        service.submitProfile(new SubmitStudentProfileCommand(4)).join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_SUBMIT");
        service.withdrawProfile(new WithdrawStudentProfileCommand(5)).join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_WITHDRAW");
        service.exportProfilePdf().join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_EXPORT_PDF");
        service.listProfileReviews(new StudentProfileReviewQuery(1, 20)).join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_REVIEW_LIST");
        service.rejectProfile(new ReviewStudentProfileCommand("app-1", "信息不完整")).join();
        assertThat(client.command).isEqualTo("STUDENT_PROFILE_REJECT");
        service.getProfile("student-9").join();
        assertThat(client.command).isEqualTo("STUDENT_GET_PROFILE");
        assertThat(client.body).isEqualTo(new EntityIdRequest("student-9"));
    }

    private static final class RecordingClient implements StudentRequestClient {
        private String command;
        private Serializable body;
        private Duration timeout;
        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            this.command = command; this.body = body; this.timeout = timeout;
            return CompletableFuture.completedFuture(ResponseBody.success(null));
        }
    }
}
