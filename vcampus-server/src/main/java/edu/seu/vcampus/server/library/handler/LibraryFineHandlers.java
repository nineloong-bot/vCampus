package edu.seu.vcampus.server.library.handler;

import edu.seu.vcampus.common.library.LibraryFineQuery;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.library.service.LibraryFineService;
import edu.seu.vcampus.server.routing.MessageRouter;
import java.io.Serializable;
import java.util.function.Supplier;

/** Authorized fine queries and borrower-confirmed payments, durably deduplicated by loan in the wallet. */
public final class LibraryFineHandlers {
    private LibraryFineHandlers() { }

    /** Registers fine endpoints; payment always rechecks ownership, even when replaying a receipt. */
    public static void register(MessageRouter router, LibraryFineService service, LibraryAccessPort access) {
        router.register("LIBRARY_GET_MY_FINES", (message, context) -> safely(() -> {
            access.requireSession(message.sessionToken());
            return service.mine(message.sessionToken(), body(LibraryFineQuery.class, message.body()));
        }));
        router.register("LIBRARY_GET_ALL_FINES", (message, context) -> safely(() -> {
            access.requirePermission(message.sessionToken(), "LIBRARY_ADMIN");
            return service.all(body(LibraryFineQuery.class, message.body()));
        }));
        router.register("LIBRARY_PAY_FINE", (message, context) -> safely(() -> {
            access.requireSession(message.sessionToken());
            return service.pay(message.sessionToken(), body(String.class, message.body()));
        }));
    }

    private static <T> T body(Class<T> type, Serializable value) {
        if (!type.isInstance(value)) throw new IllegalArgumentException("Invalid fine request");
        return type.cast(value);
    }

    private static <T extends Serializable> ResponseBody<T> safely(Supplier<T> action) {
        try {
            return ResponseBody.success(action.get());
        } catch (RuntimeException error) {
            return LibraryHandlerErrorMapper.failure(error);
        }
    }
}
