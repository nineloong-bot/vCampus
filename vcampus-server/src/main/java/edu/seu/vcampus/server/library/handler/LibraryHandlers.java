package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.library.service.LibraryService;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Supplier;

/** Registers all public and administrative library commands. */
public final class LibraryHandlers {
    private static final String ADMIN_PERMISSION = "LIBRARY_ADMIN";

    private LibraryHandlers() {
    }

    public static void register(MessageRouter router, LibraryService service,
            LibraryAccessPort access) {
        register(router, service, access, null);
    }

    /** Registers commands and persistently deduplicates writes when the runtime provides it. */
    public static void register(MessageRouter router, LibraryService service,
            LibraryAccessPort access, RequestDeduplicator deduplicator) {
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(access, "access");
        router.register("LIBRARY_SEARCH_BOOKS", (message, context) -> {
            return safely(() -> { access.requireSession(message.sessionToken());
                return ResponseBody.success(service.searchBooks(requireBody(BookSearchQuery.class, message.body()))); });
        });
        router.register("LIBRARY_GET_BOOK", (message, context) -> {
            return safely(() -> { access.requireSession(message.sessionToken());
                return ResponseBody.success(service.getBook(requireBody(String.class, message.body()))); });
        });
        router.register("LIBRARY_SEARCH_MANAGED_BOOKS", (message, context) -> safely(() -> {
            access.requirePermission(message.sessionToken(), ADMIN_PERMISSION);
            return ResponseBody.success(service.searchManagedBooks(
                    requireBody(BookSearchQuery.class, message.body())));
        }));
        registerWrite(router, "LIBRARY_BORROW", BorrowBookCommand.class, access, deduplicator,
                (token, body) -> service.borrow(token, body));
        registerWrite(router, "LIBRARY_RETURN", ReturnBookCommand.class, access, deduplicator,
                (token, body) -> service.returnBook(token, body));
        registerWrite(router, "LIBRARY_RENEW", RenewLoanCommand.class, access, deduplicator,
                (token, body) -> service.renew(token, body));
        router.register("LIBRARY_GET_MY_CURRENT_LOANS", (message, context) -> {
            return safely(() -> { access.requireSession(message.sessionToken());
                requireBody(EmptyRequest.class, message.body());
                return ResponseBody.success(new java.util.ArrayList<>(service.getCurrentLoans(message.sessionToken()))); });
        });
        router.register("LIBRARY_GET_MY_LOAN_HISTORY", (message, context) ->
                safely(() -> { access.requireSession(message.sessionToken());
                    return ResponseBody.success(service.getLoanHistory(message.sessionToken(),
                            requireBody(LoanHistoryQuery.class, message.body()))); }));
        registerAdmin(router, "LIBRARY_CREATE_BOOK", CreateBookCommand.class, access, deduplicator,
                (token, body) -> service.createBook(body));
        registerAdmin(router, "LIBRARY_UPDATE_BOOK", UpdateBookCommand.class, access, deduplicator,
                (token, body) -> service.updateBook(body));
        registerAdmin(router, "LIBRARY_ADD_COPY", AddBookCopyCommand.class, access, deduplicator,
                (token, body) -> service.addCopy(body));
        registerAdmin(router, "LIBRARY_CHANGE_COPY_STATUS", ChangeCopyStatusCommand.class, access, deduplicator,
                (token, body) -> service.changeCopyStatus(body));
        registerAdmin(router, "LIBRARY_RESOLVE_LOAN", AdminResolveLoanCommand.class, access, deduplicator,
                (token, body) -> service.resolveLoan(body));
        router.register("LIBRARY_SEARCH_ALL_LOANS", (message, context) -> safely(() -> {
            access.requirePermission(message.sessionToken(), ADMIN_PERMISSION);
            return ResponseBody.success(service.searchAllLoans(requireBody(AdminLoanSearchQuery.class, message.body())));
        }));
        router.register("LIBRARY_GET_POLICIES", (message, context) -> safely(() -> {
            access.requirePermission(message.sessionToken(), ADMIN_PERMISSION);
            requireBody(EmptyRequest.class, message.body());
            return ResponseBody.success(new java.util.ArrayList<>(service.getPolicies()));
        }));
        registerAdmin(router, "LIBRARY_UPDATE_POLICY", UpdateLibraryPolicyCommand.class, access, deduplicator,
                (token, body) -> service.updatePolicy(body));
    }

    private static <T extends java.io.Serializable, R extends java.io.Serializable> void registerAdmin(
            MessageRouter router, String command, Class<T> bodyType, LibraryAccessPort access,
            RequestDeduplicator deduplicator, AdminAction<T, R> action) {
        router.register(command, (message, context) -> safely(() -> {
            access.requirePermission(message.sessionToken(), ADMIN_PERMISSION);
            T body = requireBody(bodyType, message.body());
            return deduplicate(deduplicator, message, context.connectionId(),
                    () -> safely(() -> ResponseBody.success(
                            action.apply(message.sessionToken(), body))));
        }));
    }

    private static <T extends Serializable, R extends Serializable> void registerWrite(
            MessageRouter router, String command, Class<T> bodyType, LibraryAccessPort access,
            RequestDeduplicator deduplicator, AdminAction<T, R> action) {
        router.register(command, (message, context) -> safely(() -> {
            access.requireSession(message.sessionToken());
            T body = requireBody(bodyType, message.body());
            return deduplicate(deduplicator, message, context.connectionId(),
                    () -> safely(() -> ResponseBody.success(
                            action.apply(message.sessionToken(), body))));
        }));
    }

    private static <T extends Serializable> ResponseBody<T> deduplicate(RequestDeduplicator deduplicator,
            edu.seu.vcampus.common.protocol.Message message, String connectionId,
            Supplier<ResponseBody<T>> action) {
        return deduplicator == null ? action.get() : deduplicator.executeOnce(message, null, connectionId, action);
    }

    private static <T extends Serializable> ResponseBody<T> safely(Supplier<ResponseBody<T>> action) {
        try { return action.get(); }
        catch (RuntimeException error) { return LibraryHandlerErrorMapper.failure(error); }
    }

    private static <T> T requireBody(Class<T> type, Serializable body) {
        if (!type.isInstance(body)) throw new IllegalArgumentException("COMMON_VALIDATION_FAILED");
        return type.cast(body);
    }

    @FunctionalInterface
    private interface AdminAction<T, R> {
        R apply(String sessionToken, T body);
    }
}
