package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.LibraryPolicyView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import edu.seu.vcampus.common.library.ChangeCopyStatusCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.server.library.service.LibraryService;
import edu.seu.vcampus.server.library.service.CopyUnavailableException;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import edu.seu.vcampus.common.protocol.EmptyRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    void checksAdminPermissionBeforeLoadingPolicies() {
        LibraryHandlers.register(router, service, access);
        List<LibraryPolicyView> policies = List.of(
                new LibraryPolicyView("STUDENT", 5, 30, 1, 15, 2));
        when(service.getPolicies()).thenReturn(policies);

        assertThat(route("LIBRARY_GET_POLICIES", EmptyRequest.INSTANCE)).isEqualTo(policies);

        verify(access).requirePermission("token", "LIBRARY_ADMIN");
        verify(service).getPolicies();
    }

    @Test
    void checksAdminPermissionBeforeSearchingManagedCatalog() {
        LibraryHandlers.register(router, service, access);
        BookSearchQuery query = new BookSearchQuery("", null, false, 1, 100);
        PageResult<BookSummary> page = new PageResult<>(List.of(), 1, 100, 0);
        when(service.searchManagedBooks(query)).thenReturn(page);

        assertThat(route("LIBRARY_SEARCH_MANAGED_BOOKS", query)).isEqualTo(page);

        verify(access).requirePermission("token", "LIBRARY_ADMIN");
        verify(service).searchManagedBooks(query);
    }

    @Test
    void mapsBusinessFailureInsteadOfEscapingTheRouter() {
        LibraryHandlers.register(router, service, access);
        when(service.borrow(anyString(), any())).thenThrow(
                new edu.seu.vcampus.server.library.service.CopyUnavailableException("copy-1"));
        Message message = new Message("request-1", MessageType.REQUEST, "LIBRARY_BORROW",
                "token", new BorrowBookCommand("copy-1"), 1L);

        var response = router.route(message, new ClientContext("connection-1", "127.0.0.1"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("LIBRARY_COPY_UNAVAILABLE");
    }

    @Test
    void mapsCopyVersionConflictToAnActionableResponse() {
        LibraryHandlers.register(router, service, access);
        ChangeCopyStatusCommand command = new ChangeCopyStatusCommand(
                "copy-1", CopyStatus.DAMAGED, 0);
        when(service.changeCopyStatus(command)).thenThrow(
                new java.util.ConcurrentModificationException("Book copy changed: copy-1"));
        Message message = new Message("request-1", MessageType.REQUEST,
                "LIBRARY_CHANGE_COPY_STATUS", "token", command, 1L);

        var response = router.route(message,
                new ClientContext("connection-1", "127.0.0.1"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo("LIBRARY_COPY_STALE");
        assertThat(response.message()).contains("刷新副本");
    }

    @Test
    void routesWritesThroughRequestDeduplication() {
        RequestDeduplicator deduplicator = mock(RequestDeduplicator.class);
        doAnswer(invocation -> ((java.util.function.Supplier<?>) invocation.getArgument(3)).get())
                .when(deduplicator).executeOnce(any(), isNull(), eq("connection-1"), any());
        LibraryHandlers.register(router, service, access, deduplicator);
        when(service.borrow("token", new BorrowBookCommand("copy-1"))).thenReturn(loan);

        assertThat(route("LIBRARY_BORROW", new BorrowBookCommand("copy-1"))).isEqualTo(loan);

        verify(deduplicator).executeOnce(any(), isNull(), eq("connection-1"), any());
    }

    @Test
    void completesDeduplicationWithMappedBusinessFailure() {
        RequestDeduplicator deduplicator = mock(RequestDeduplicator.class);
        AtomicBoolean supplierReturned = new AtomicBoolean();
        doAnswer(invocation -> {
            Object response = ((java.util.function.Supplier<?>) invocation.getArgument(3)).get();
            supplierReturned.set(true);
            return response;
        }).when(deduplicator).executeOnce(any(), isNull(), eq("connection-1"), any());
        LibraryHandlers.register(router, service, access, deduplicator);
        BorrowBookCommand command = new BorrowBookCommand("copy-1");
        when(service.borrow("token", command)).thenThrow(new CopyUnavailableException("copy-1"));
        Message message = new Message("request-1", MessageType.REQUEST,
                "LIBRARY_BORROW", "token", command, 1L);

        var response = router.route(message,
                new ClientContext("connection-1", "127.0.0.1"));

        assertThat(response.code()).isEqualTo("LIBRARY_COPY_UNAVAILABLE");
        assertThat(supplierReturned).isTrue();
    }

    private Object route(String command, java.io.Serializable body) {
        Message message = new Message("request-1", MessageType.REQUEST, command,
                "token", body, 1L);
        return router.route(message, new ClientContext("connection-1", "127.0.0.1")).data();
    }
}
