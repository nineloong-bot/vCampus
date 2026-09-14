package edu.seu.vcampus.server.student.majortransfer.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.student.EntityIdRequest;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferDecision;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationQuery;
import edu.seu.vcampus.common.student.majortransfer.ReviewMajorTransferQualificationCommand;
import edu.seu.vcampus.common.student.majortransfer.ReviewMajorTransferSourceCommand;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferBatchCommand;
import edu.seu.vcampus.common.student.majortransfer.RecordMajorTransferScoreCommand;
import edu.seu.vcampus.common.student.majortransfer.FinalizeMajorTransferCommand;
import edu.seu.vcampus.common.student.majortransfer.ExecuteMajorTransferCommand;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferOptionCommand;
import java.math.BigDecimal;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.student.handler.StudentPrincipal;
import edu.seu.vcampus.server.student.majortransfer.security.MajorTransferCollegeAuthorizationService;
import edu.seu.vcampus.server.student.majortransfer.service.MajorTransferService;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MajorTransferRoleAuthorizationTest {
    private static final String APPLICATION = "application-1";

    @Test
    void studentAdministratorCannotPerformCollegeReview() {
        Fixture fixture = fixture("STUDENT_ADMIN");

        var result = fixture.route("MAJOR_TRANSFER_REVIEW_SOURCE", sourceReview());

        assertThat(result.code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).reviewSource(anyString(), any());
    }

    @Test
    void collegeAdministratorCannotSaveBatch() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        Instant start = Instant.parse("2026-09-01T00:00:00Z");

        var result = fixture.route("MAJOR_TRANSFER_SAVE_BATCH", new SaveMajorTransferBatchCommand(
                null, "test", MajorTransferBatchStatus.DRAFT, start, start.plusSeconds(3600),
                null, null, null, 0));

        assertThat(result.code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).saveBatch(anyString(), any());
    }

    @Test
    void sourceCollegeAdministratorCanInvokeSourceReview() {
        Fixture fixture = fixture("COLLEGE_ADMIN");

        var result = fixture.route("MAJOR_TRANSFER_REVIEW_SOURCE", sourceReview());

        assertThat(result.success()).isTrue();
        verify(fixture.scope).requireSourceApproval("operator", APPLICATION);
        verify(fixture.service).reviewSource("operator", sourceReview(), "managed-department");
    }

    @Test
    void targetCollegeAdministratorCanInvokeQualificationReview() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        var command = new ReviewMajorTransferQualificationCommand(
                APPLICATION, MajorTransferDecision.APPROVE, "ok", 0);

        var result = fixture.route("MAJOR_TRANSFER_REVIEW_QUALIFICATION", command);

        assertThat(result.success()).isTrue();
        verify(fixture.scope).requireTargetApproval("operator", APPLICATION);
        verify(fixture.service).reviewQualification("operator", command, "managed-department");
    }

    @Test
    void unrelatedCollegeAdministratorCannotReadDetailOrAttachment() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        doThrow(new IllegalArgumentException("COMMON_FORBIDDEN"))
                .when(fixture.scope).requireCanRead("operator", APPLICATION);
        doThrow(new IllegalArgumentException("COMMON_FORBIDDEN"))
                .when(fixture.scope).requireCanReadAttachment("operator", "attachment-1");

        assertThat(fixture.route("MAJOR_TRANSFER_GET_APPLICATION",
                new EntityIdRequest(APPLICATION)).code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(fixture.route("MAJOR_TRANSFER_GET_ATTACHMENT",
                new EntityIdRequest("attachment-1")).code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).getApplicationDetail(anyString());
        verify(fixture.service, never()).getAttachment(anyString());
    }

    @Test
    void collegeListUsesOnlyItsResolvedDepartment() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        var query = new MajorTransferApplicationQuery("batch", null, null);
        when(fixture.scope.findActiveDepartmentId("operator")).thenReturn("managed-department");

        var result = fixture.route("MAJOR_TRANSFER_LIST_APPLICATIONS", query);

        assertThat(result.success()).isTrue();
        verify(fixture.service).listApplicationsForCollege(query, "managed-department");
        verify(fixture.service, never()).listApplications(query);
    }

    @Test
    void collegeAdministratorCanRecordScoreWithTargetScope() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        var command = new RecordMajorTransferScoreCommand(APPLICATION, BigDecimal.valueOf(85), BigDecimal.valueOf(90), 0);

        var result = fixture.route("MAJOR_TRANSFER_RECORD_SCORE", command);

        assertThat(result.success()).isTrue();
        verify(fixture.scope).requireTargetApproval("operator", APPLICATION);
        verify(fixture.service).recordScore("operator", command, "managed-department");
    }

    @Test
    void collegeAdministratorCanFinalizeWithTargetScope() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        var command = new FinalizeMajorTransferCommand(APPLICATION, 0);

        var result = fixture.route("MAJOR_TRANSFER_FINALIZE", command);

        assertThat(result.success()).isTrue();
        verify(fixture.scope).requireTargetApproval("operator", APPLICATION);
        verify(fixture.service).finalizeApproval("operator", command, "managed-department");
    }

    @Test
    void studentAdministratorCannotRecordScore() {
        Fixture fixture = fixture("STUDENT_ADMIN");
        var command = new RecordMajorTransferScoreCommand(APPLICATION, BigDecimal.valueOf(85), BigDecimal.valueOf(90), 0);

        var result = fixture.route("MAJOR_TRANSFER_RECORD_SCORE", command);

        assertThat(result.code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).recordScore(anyString(), any());
    }

    @Test
    void studentAdministratorCannotFinalize() {
        Fixture fixture = fixture("STUDENT_ADMIN");
        var command = new FinalizeMajorTransferCommand(APPLICATION, 0);

        var result = fixture.route("MAJOR_TRANSFER_FINALIZE", command);

        assertThat(result.code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).finalizeApproval(anyString(), any());
    }

    @Test
    void studentAdministratorCannotReadConcreteApplications() {
        Fixture fixture = fixture("STUDENT_ADMIN");

        assertThat(fixture.route("MAJOR_TRANSFER_LIST_APPLICATIONS",
                new MajorTransferApplicationQuery("batch", null, null)).code())
                .isEqualTo("COMMON_FORBIDDEN");
        assertThat(fixture.route("MAJOR_TRANSFER_GET_APPLICATION",
                new EntityIdRequest(APPLICATION)).code()).isEqualTo("COMMON_FORBIDDEN");
        assertThat(fixture.route("MAJOR_TRANSFER_GET_ATTACHMENT",
                new EntityIdRequest("attachment-1")).code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).listApplications(any());
        verify(fixture.service, never()).getApplicationDetail(anyString());
        verify(fixture.service, never()).getAttachment(anyString());
    }

    @Test
    void targetCollegeAdministratorCanExecuteTransfer() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        var command = new ExecuteMajorTransferCommand(APPLICATION, "class-2", 0);

        var result = fixture.route("MAJOR_TRANSFER_EXECUTE", command);

        assertThat(result.success()).isTrue();
        verify(fixture.scope).requireTargetApproval("operator", APPLICATION);
        verify(fixture.service).execute("operator", command, "managed-department");
    }

    @Test
    void studentAdministratorCannotExecuteTransfer() {
        Fixture fixture = fixture("STUDENT_ADMIN");

        var result = fixture.route("MAJOR_TRANSFER_EXECUTE",
                new ExecuteMajorTransferCommand(APPLICATION, "class-2", 0));

        assertThat(result.code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).execute(anyString(),
                any(ExecuteMajorTransferCommand.class), anyString());
    }

    @Test
    void collegeAdministratorCanSaveOwnOption() {
        Fixture fixture = fixture("COLLEGE_ADMIN");
        when(fixture.scope.findActiveDepartmentId("operator"))
                .thenReturn("managed-department");
        var command = new SaveMajorTransferOptionCommand(null, "batch", "major", "2025",
                10, 20, 60.0, 60.0, 50, 50, false, "requirements", true, 0);

        var result = fixture.route("MAJOR_TRANSFER_SAVE_OPTION", command);

        assertThat(result.success()).isTrue();
        verify(fixture.scope).findActiveDepartmentId("operator");
        verify(fixture.service).saveOption("operator", command, "managed-department");
    }

    @Test
    void studentAdministratorCannotSaveCollegeOption() {
        Fixture fixture = fixture("STUDENT_ADMIN");
        var command = new SaveMajorTransferOptionCommand(null, "batch", "major", "2025",
                10, 20, 60.0, 60.0, 50, 50, false, "requirements", true, 0);

        assertThat(fixture.route("MAJOR_TRANSFER_SAVE_OPTION", command).code())
                .isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).saveOption(anyString(),
                any(SaveMajorTransferOptionCommand.class), anyString());
    }

    @Test
    void collegeAdministratorListsOnlyOwnOptions() {
        Fixture fixture = fixture("COLLEGE_ADMIN");

        var result = fixture.route("MAJOR_TRANSFER_LIST_OPTIONS",
                new EntityIdRequest("batch"));

        assertThat(result.success()).isTrue();
        verify(fixture.service).listOptionsForCollege("batch", "managed-department");
        verify(fixture.service, never()).listOptions("batch");
    }

    @Test
    void studentAdministratorCannotListCollegeOptions() {
        Fixture fixture = fixture("STUDENT_ADMIN");

        assertThat(fixture.route("MAJOR_TRANSFER_LIST_OPTIONS",
                new EntityIdRequest("batch")).code()).isEqualTo("COMMON_FORBIDDEN");
        verify(fixture.service, never()).listOptions(anyString());
        verify(fixture.service, never()).listOptionsForCollege(anyString(), anyString());
    }

    private static ReviewMajorTransferSourceCommand sourceReview() {
        return new ReviewMajorTransferSourceCommand(APPLICATION, MajorTransferDecision.APPROVE,
                true, true, true, "ok", 0);
    }

    private static Fixture fixture(String role) {
        MajorTransferService service = mock(MajorTransferService.class);
        MajorTransferCollegeAuthorizationService scope =
                mock(MajorTransferCollegeAuthorizationService.class);
        when(scope.findActiveDepartmentId("operator")).thenReturn("managed-department");
        MessageRouter router = new MessageRouter(Map.of());
        new MajorTransferHandlers(service,
                token -> new StudentPrincipal("operator", Set.of(role), Set.of()),
                (request, principal, action) -> action.get(), scope).register(router);
        return new Fixture(router, service, scope);
    }

    private record Fixture(MessageRouter router, MajorTransferService service,
                           MajorTransferCollegeAuthorizationService scope) {
        private edu.seu.vcampus.common.protocol.ResponseBody<?> route(
                String command, Serializable body) {
            Message request = new Message("request-" + command, MessageType.REQUEST, command,
                    "token", body, System.currentTimeMillis());
            return router.route(request, new ClientContext("test", "127.0.0.1"));
        }
    }
}
