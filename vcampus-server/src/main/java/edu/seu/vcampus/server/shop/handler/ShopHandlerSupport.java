package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.routing.MessageHandler;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.BiFunction;

final class ShopHandlerSupport {
    private final ShopUserPort users;
    private final RequestDeduplicator deduplicator;
    private final ShopBusinessLogger log;

    ShopHandlerSupport(ShopUserPort users, RequestDeduplicator deduplicator,
            ShopBusinessLogger log) {
        this.users = Objects.requireNonNull(users, "users");
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
        this.log = Objects.requireNonNull(log, "log");
    }

    <T extends Serializable, R extends Serializable> MessageHandler read(
            Class<T> type, BiFunction<String, T, R> operation) {
        return handler(type, operation, false);
    }

    <T extends Serializable, R extends Serializable> MessageHandler write(
            Class<T> type, BiFunction<String, T, R> operation) {
        return handler(type, operation, true);
    }

    private <T extends Serializable, R extends Serializable> MessageHandler handler(
            Class<T> type, BiFunction<String, T, R> operation, boolean write) {
        return (message, context) -> {
            long started = System.nanoTime();
            String actorId = "anonymous";
            ResponseBody<? extends Serializable> response;
            try {
                ShopUser actor = users.requireUser(message.sessionToken());
                actorId = actor.userId();
                if (message.body() == null) {
                    throw new IllegalArgumentException("request body is required");
                }
                T body = type.cast(message.body());
                if (write) {
                    String finalActorId = actorId;
                    response = deduplicator.executeOnce(message, finalActorId, context.connectionId(),
                            () -> execute(message, body, operation));
                } else {
                    response = execute(message, body, operation);
                }
            } catch (RuntimeException error) {
                response = failure(error);
            }
            log.commandCompleted(message, actorId, response.code(),
                    (System.nanoTime() - started) / 1_000_000);
            return response;
        };
    }

    private static <T extends Serializable, R extends Serializable> ResponseBody<R> execute(
            Message message, T body, BiFunction<String, T, R> operation) {
        try {
            return ResponseBody.success(operation.apply(message.sessionToken(), body));
        } catch (RuntimeException error) {
            @SuppressWarnings("unchecked") ResponseBody<R> response = (ResponseBody<R>) failure(error);
            return response;
        }
    }

    private static ResponseBody<? extends Serializable> failure(RuntimeException error) {
        if (error instanceof ShopAccessException access) {
            return ResponseBody.failure(access.code(), "Authentication failed", null);
        }
        if (error instanceof ShopException shop) {
            return ResponseBody.failure(shop.code().name(), "Shop request failed", null);
        }
        if (error instanceof IllegalArgumentException || error instanceof NullPointerException
                || error instanceof ClassCastException || error instanceof SecurityException) {
            return ResponseBody.failure("COMMON_VALIDATION_FAILED", "Invalid request", null);
        }
        return ResponseBody.failure("COMMON_INTERNAL_ERROR", "Internal error", null);
    }
}
