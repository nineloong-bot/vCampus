package edu.seu.vcampus.client.library.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.library.LibraryPolicyView;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryClientServiceTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private final ClientConnection connection = mock(ClientConnection.class);
    private final LibraryClientService service = new LibraryClientService(connection, TIMEOUT);
    private final LoanView loan = new LoanView("loan-1", "copy-1", "book-1", "user-1",
            Instant.parse("2026-08-28T08:00:00Z"), Instant.parse("2026-09-27T08:00:00Z"),
            null, 0, LoanStatus.ACTIVE, 0);

    @Test
    void sendsExactLifecycleCommandsAsynchronously() {
        BorrowBookCommand borrow = new BorrowBookCommand("copy-1");
        ReturnBookCommand returned = new ReturnBookCommand("loan-1", 0);
        RenewLoanCommand renew = new RenewLoanCommand("loan-1", 0);
        when(connection.<LoanView>send("LIBRARY_BORROW", borrow, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(loan)));
        when(connection.<LoanView>send("LIBRARY_RETURN", returned, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(loan)));
        when(connection.<LoanView>send("LIBRARY_RENEW", renew, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(loan)));

        assertThat(service.borrow(borrow).join()).isEqualTo(loan);
        assertThat(service.returnBook(returned).join()).isEqualTo(loan);
        assertThat(service.renew(renew).join()).isEqualTo(loan);
        verify(connection).send("LIBRARY_BORROW", borrow, TIMEOUT);
        verify(connection).send("LIBRARY_RETURN", returned, TIMEOUT);
        verify(connection).send("LIBRARY_RENEW", renew, TIMEOUT);
    }

    @Test
    void convertsFailedResponseToActionableException() {
        BorrowBookCommand command = new BorrowBookCommand("copy-1");
        when(connection.<LoanView>send("LIBRARY_BORROW", command, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.failure(
                        "LIBRARY_COPY_UNAVAILABLE", "馆藏副本当前不可借", null)));

        assertThatThrownBy(() -> service.borrow(command).join())
                .hasCauseInstanceOf(LibraryRequestException.class)
                .hasRootCauseMessage("馆藏副本当前不可借");
    }

    @Test
    void sendsCatalogSearchAndAdministrativePolicyCommand() {
        BookSearchQuery query = new BookSearchQuery("Java", null, true, 1, 20);
        PageResult<BookSummary> page = new PageResult<>(List.of(), 1, 20, 0);
        UpdateLibraryPolicyCommand command = new UpdateLibraryPolicyCommand(
                "STUDENT", 6, 31, 2, 16, 0);
        LibraryPolicyView policy = new LibraryPolicyView("STUDENT", 6, 31, 2, 16, 1);
        List<LibraryPolicyView> policies = List.of(policy);
        when(connection.<PageResult<BookSummary>>send("LIBRARY_SEARCH_BOOKS", query, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(page)));
        when(connection.<PageResult<BookSummary>>send(
                "LIBRARY_SEARCH_MANAGED_BOOKS", query, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(page)));
        when(connection.<LibraryPolicyView>send("LIBRARY_UPDATE_POLICY", command, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(policy)));
        when(connection.<java.util.ArrayList<LibraryPolicyView>>send(
                "LIBRARY_GET_POLICIES", EmptyRequest.INSTANCE, TIMEOUT))
                .thenReturn(CompletableFuture.completedFuture(
                        ResponseBody.success(new java.util.ArrayList<>(policies))));

        assertThat(service.searchBooks(query).join()).isEqualTo(page);
        assertThat(service.searchManagedBooks(query).join()).isEqualTo(page);
        assertThat(service.updatePolicy(command).join()).isEqualTo(policy);
        assertThat(service.getPolicies().join()).isEqualTo(policies);
    }
}
