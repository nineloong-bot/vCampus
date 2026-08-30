package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.library.service.LibraryService;
import edu.seu.vcampus.server.routing.MessageRouter;

import java.util.Objects;

/** Registers all public and administrative library commands. */
public final class LibraryHandlers {
    private static final String ADMIN_PERMISSION = "LIBRARY_ADMIN";

    private LibraryHandlers() {
    }

    public static void register(MessageRouter router, LibraryService service,
            LibraryAccessPort access) {
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(access, "access");
        router.register("LIBRARY_SEARCH_BOOKS", (message, context) -> {
            access.requireSession(message.sessionToken());
            return ResponseBody.success(service.searchBooks(
                    BookSearchQuery.class.cast(message.body())));
        });
        router.register("LIBRARY_GET_BOOK", (message, context) -> {
            access.requireSession(message.sessionToken());
            return ResponseBody.success(service.getBook(String.class.cast(message.body())));
        });
        router.register("LIBRARY_BORROW", (message, context) -> ResponseBody.success(
                service.borrow(message.sessionToken(),
                        BorrowBookCommand.class.cast(message.body()))));
        router.register("LIBRARY_RETURN", (message, context) -> ResponseBody.success(
                service.returnBook(message.sessionToken(),
                        ReturnBookCommand.class.cast(message.body()))));
        router.register("LIBRARY_RENEW", (message, context) -> ResponseBody.success(
                service.renew(message.sessionToken(),
                        RenewLoanCommand.class.cast(message.body()))));
        router.register("LIBRARY_GET_MY_CURRENT_LOANS", (message, context) -> {
            EmptyRequest.class.cast(message.body());
            return ResponseBody.success(new java.util.ArrayList<>(
                    service.getCurrentLoans(message.sessionToken())));
        });
        router.register("LIBRARY_GET_MY_LOAN_HISTORY", (message, context) ->
                ResponseBody.success(service.getLoanHistory(message.sessionToken(),
                        LoanHistoryQuery.class.cast(message.body()))));
        registerAdmin(router, "LIBRARY_CREATE_BOOK", CreateBookCommand.class, access,
                (token, body) -> service.createBook(body));
        registerAdmin(router, "LIBRARY_UPDATE_BOOK", UpdateBookCommand.class, access,
                (token, body) -> service.updateBook(body));
        registerAdmin(router, "LIBRARY_ADD_COPY", AddBookCopyCommand.class, access,
                (token, body) -> service.addCopy(body));
        registerAdmin(router, "LIBRARY_CHANGE_COPY_STATUS", ChangeCopyStatusCommand.class, access,
                (token, body) -> service.changeCopyStatus(body));
        registerAdmin(router, "LIBRARY_SEARCH_ALL_LOANS", AdminLoanSearchQuery.class, access,
                (token, body) -> service.searchAllLoans(body));
        registerAdmin(router, "LIBRARY_UPDATE_POLICY", UpdateLibraryPolicyCommand.class, access,
                (token, body) -> service.updatePolicy(body));
    }

    private static <T extends java.io.Serializable, R extends java.io.Serializable> void registerAdmin(
            MessageRouter router, String command, Class<T> bodyType, LibraryAccessPort access,
            AdminAction<T, R> action) {
        router.register(command, (message, context) -> {
            access.requirePermission(message.sessionToken(), ADMIN_PERMISSION);
            return ResponseBody.success(action.apply(message.sessionToken(),
                    bodyType.cast(message.body())));
        });
    }

    @FunctionalInterface
    private interface AdminAction<T, R> {
        R apply(String sessionToken, T body);
    }
}
