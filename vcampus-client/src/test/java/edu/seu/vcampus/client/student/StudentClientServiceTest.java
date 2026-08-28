package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.CreateStudentAdmissionCommand;
import edu.seu.vcampus.common.student.StudentAdmissionResult;
import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.common.student.SaveDepartmentCommand;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class StudentClientServiceTest {
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
    void organizationSaveUsesAdministrativeMessageContract() {
        var client = new RecordingClient();
        var service = new StudentClientService(client, Duration.ofSeconds(3));
        var command = new SaveDepartmentCommand(null, "CS", "计算机学院", true, 0);

        service.saveDepartment(command).join();

        assertThat(client.command).isEqualTo("STUDENT_SAVE_DEPARTMENT");
        assertThat(client.body).isSameAs(command);
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
