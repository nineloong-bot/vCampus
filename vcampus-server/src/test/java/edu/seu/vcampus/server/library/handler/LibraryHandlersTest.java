package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.LibraryPolicyView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.server.library.service.LibraryService;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryHandlersTest {
    private final LibraryService service = mock(LibraryService.class);
    private final LibraryAccessPort access = mock(LibraryAccessPort.class);
    private final MessageRouter router = new MessageRouter(Map.of());
    private final LoanView loan = new LoanView("loan-1", "copy-1", "book-1", "user-1",
            Instant.parse("2026-08-28T08:00:00Z"), Instant.parse("2026-09-27T08:00:00Z"),
            null, 0, LoanStatus.ACTIVE, 0);

    @Test
    void registersBorrowReturnAndRenewWithSessionToken() {
        LibraryHandlers.register(router, service, access);
        when(service.borrow("token", new BorrowBookCommand("copy-1"))).thenReturn(loan);
        when(service.returnBook("token", new ReturnBookCommand("loan-1", 0))).thenReturn(loan);
        when(service.renew("token", new RenewLoanCommand("loan-1", 0))).thenReturn(loan);

        assertThat(route("LIBRARY_BORROW", new BorrowBookCommand("copy-1"))).isEqualTo(loan);
        assertThat(route("LIBRARY_RETURN", new ReturnBookCommand("loan-1", 0))).isEqualTo(loan);
        assertThat(route("LIBRARY_RENEW", new RenewLoanCommand("loan-1", 0))).isEqualTo(loan);

        verify(service).borrow("token", new BorrowBookCommand("copy-1"));
        verify(service).returnBook("token", new ReturnBookCommand("loan-1", 0));
        verify(service).renew("token", new RenewLoanCommand("loan-1", 0));
    }

    @Test
    void checksAdminPermissionBeforePolicyUpdate() {
        LibraryHandlers.register(router, service, access);
        UpdateLibraryPolicyCommand command = new UpdateLibraryPolicyCommand(
                "STUDENT", 6, 31, 2, 16, 0);
        LibraryPolicyView policy = new LibraryPolicyView("STUDENT", 6, 31, 2, 16, 1);
        when(service.updatePolicy(command)).thenReturn(policy);

        assertThat(route("LIBRARY_UPDATE_POLICY", command)).isEqualTo(policy);

        verify(access).requirePermission("token", "LIBRARY_ADMIN");
        verify(service).updatePolicy(command);
    }

    private Object route(String command, java.io.Serializable body) {
        Message message = new Message("request-1", MessageType.REQUEST, command,
                "token", body, 1L);
        return router.route(message, new ClientContext("connection-1", "127.0.0.1")).data();
    }
}
