package edu.seu.vcampus.client.library.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Asynchronous client facade for implemented library lifecycle commands. */
public final class LibraryClientService {
    private final ClientConnection connection;
    private final Duration timeout;

    public LibraryClientService(ClientConnection connection, Duration timeout) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    public CompletableFuture<LoanView> borrow(BorrowBookCommand command) {
        return request("LIBRARY_BORROW", command);
    }

    public CompletableFuture<LoanView> returnBook(ReturnBookCommand command) {
        return request("LIBRARY_RETURN", command);
    }

    public CompletableFuture<LoanView> renew(RenewLoanCommand command) {
        return request("LIBRARY_RENEW", command);
    }

    public CompletableFuture<PageResult<BookSummary>> searchBooks(BookSearchQuery query) {
        return request("LIBRARY_SEARCH_BOOKS", query);
    }

    public CompletableFuture<BookDetail> getBook(String bookId) {
        return request("LIBRARY_GET_BOOK", bookId);
    }

    public CompletableFuture<List<LoanView>> getCurrentLoans() {
        CompletableFuture<ArrayList<LoanView>> response = request(
                "LIBRARY_GET_MY_CURRENT_LOANS", EmptyRequest.INSTANCE);
        return response.thenApply(List::copyOf);
    }

    public CompletableFuture<PageResult<LoanView>> getLoanHistory(LoanHistoryQuery query) {
        return request("LIBRARY_GET_MY_LOAN_HISTORY", query);
    }

    public CompletableFuture<BookView> createBook(CreateBookCommand command) {
        return request("LIBRARY_CREATE_BOOK", command);
    }

    public CompletableFuture<BookView> updateBook(UpdateBookCommand command) {
        return request("LIBRARY_UPDATE_BOOK", command);
    }

    public CompletableFuture<BookCopyView> addCopy(AddBookCopyCommand command) {
        return request("LIBRARY_ADD_COPY", command);
    }

    public CompletableFuture<BookCopyView> changeCopyStatus(ChangeCopyStatusCommand command) {
        return request("LIBRARY_CHANGE_COPY_STATUS", command);
    }

    public CompletableFuture<PageResult<LoanView>> searchAllLoans(AdminLoanSearchQuery query) {
        return request("LIBRARY_SEARCH_ALL_LOANS", query);
    }

    public CompletableFuture<LibraryPolicyView> updatePolicy(
            UpdateLibraryPolicyCommand command) {
        return request("LIBRARY_UPDATE_POLICY", command);
    }

    private <T extends Serializable> CompletableFuture<T> request(
            String command, Serializable body) {
        return connection.<T>send(command, body, timeout)
                .thenApply(LibraryClientService::requireSuccess);
    }

    private static <T extends Serializable> T requireSuccess(ResponseBody<T> response) {
        if (!response.success()) {
            throw new LibraryRequestException(response.code(), response.message());
        }
        return response.data();
    }
}
