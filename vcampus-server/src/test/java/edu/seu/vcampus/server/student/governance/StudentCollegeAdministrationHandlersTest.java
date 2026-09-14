package edu.seu.vcampus.server.student.governance;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministrationSnapshot;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.security.AuthorizationPort;
import edu.seu.vcampus.server.security.UserIdentity;
import edu.seu.vcampus.server.student.support.StudentAccessTestDatabase;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StudentCollegeAdministrationHandlersTest {
    private static final ClientContext CONTEXT =
            new ClientContext("connection", "127.0.0.1");

    @Test
    void studentAdministratorCanUseAllGovernanceCommands() {
        var service = mock(StudentCollegeAdministrationService.class);
        org.mockito.Mockito.when(service.list()).thenReturn(
                new StudentCollegeAdministrationSnapshot(List.of(), List.of()));
        MessageRouter router = router(service, UserRole.STUDENT_ADMIN, null);
        var assign = new AssignStudentCollegeAdministratorCommand("department", "user", 0);
        var transfer = new TransferStudentCollegeAdministratorCommand(
                "user", "department", "target", 0, 0);
        var deactivate = new DeactivateStudentCollegeAdministratorCommand(
                "department", "user", 0);

        assertThat(route(router, "STUDENT_COLLEGE_ADMIN_SEARCH", EmptyRequest.INSTANCE).success())
                .isTrue();
        assertThat(route(router, "STUDENT_COLLEGE_ADMIN_ASSIGN", assign).success()).isTrue();
        assertThat(route(router, "STUDENT_COLLEGE_ADMIN_TRANSFER", transfer).success()).isTrue();
        assertThat(route(router, "STUDENT_COLLEGE_ADMIN_DEACTIVATE", deactivate).success())
                .isTrue();
        verify(service).assign("actor", assign);
        verify(service).transfer("actor", transfer);
        verify(service).deactivate("actor", deactivate);
    }

    @Test
    void collegeAdministratorCannotGovernAdministratorsEvenWithPermissions() {
        var service = mock(StudentCollegeAdministrationService.class);
        MessageRouter router = router(service, UserRole.COLLEGE_ADMIN, null);

        assertThat(route(router, "STUDENT_COLLEGE_ADMIN_SEARCH", EmptyRequest.INSTANCE).code())
                .isEqualTo("AUTH_FORBIDDEN");
        assertThat(route(router, "STUDENT_COLLEGE_ADMIN_ASSIGN",
                new AssignStudentCollegeAdministratorCommand("department", "user", 0)).code())
                .isEqualTo("AUTH_FORBIDDEN");
    }

    @Test
    void repeatedWriteRequestIsExecutedOnce() throws Exception {
        var service = mock(StudentCollegeAdministrationService.class);
        var database = new StudentAccessTestDatabase();
        MessageRouter router = router(service, UserRole.STUDENT_ADMIN,
                new RequestDeduplicator(database.transactions()));
        var command = new AssignStudentCollegeAdministratorCommand("department", "user", 0);
        Message message = request("request-1", "STUDENT_COLLEGE_ADMIN_ASSIGN", command);

        assertThat(router.route(message, CONTEXT).success()).isTrue();
        assertThat(router.route(message, CONTEXT).success()).isTrue();
        verify(service).assign("actor", command);
    }

    private static MessageRouter router(StudentCollegeAdministrationService service,
            UserRole role, RequestDeduplicator deduplicator) {
        MessageRouter router = new MessageRouter(Map.of());
        new StudentCollegeAdministrationHandlers(router, service,
                new FixedAuthorization(role), deduplicator);
        return router;
    }

    private static ResponseBody<?> route(MessageRouter router,
            String command, Serializable body) {
        return router.route(request("request-" + command, command, body), CONTEXT);
    }

    private static Message request(String requestId, String command, Serializable body) {
        return new Message(requestId, MessageType.REQUEST, command, "token", body, 0);
    }

    private record FixedAuthorization(UserRole role) implements AuthorizationPort {
        @Override public UserIdentity requireSession(String token) {
            return new UserIdentity("actor", "ACTOR", role, AccountStatus.ACTIVE);
        }
        @Override public void requirePermission(String token, String permission) { }
    }
}
