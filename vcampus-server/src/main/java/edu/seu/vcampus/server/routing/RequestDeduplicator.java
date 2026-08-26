package edu.seu.vcampus.server.routing;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Persists write-request outcomes and replays them by request id alone. */
public final class RequestDeduplicator {
    private final TransactionManager transactions;
    private final ResourceLockManager locks;

    /** Creates a request deduplicator with local striped request locks. */
    public RequestDeduplicator(TransactionManager transactions) {
        this(transactions, new StripedResourceLockManager());
    }

    /** Creates a request deduplicator with explicit infrastructure collaborators. */
    public RequestDeduplicator(TransactionManager transactions, ResourceLockManager locks) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
    }

    /** Executes an action once and replays its completed response for the same request id. */
    public <T extends Serializable> ResponseBody<T> executeOnce(
            Message request, String userId, String clientInstanceId,
            Supplier<ResponseBody<T>> action) {
        requireRequestId(request);
        return locks.withLocks(List.of(new ResourceKey("REQUEST", request.requestId())), () -> {
            Optional<ResponseBody<?>> replay = replayCompleted(request.requestId());
            if (replay.isPresent()) {
                return cast(replay.get());
            }
            boolean claimed = transactions.inTransaction(connection -> claim(
                    new TransactionContext(connection, userId, clientInstanceId), request));
            if (!claimed) {
                return cast(ResponseBody.failure("COMMON_REQUEST_IN_PROGRESS",
                        "请求正在处理中", null));
            }
            ResponseBody<T> response = action.get();
            Message responseMessage = new Message(request.requestId(), MessageType.RESPONSE,
                    request.command(), request.sessionToken(), response, System.currentTimeMillis());
            transactions.inTransaction(connection -> {
                complete(new TransactionContext(connection, userId, clientInstanceId), responseMessage);
                return null;
            });
            return response;
        });
    }

    /** Returns a stored completed response, if one exists. */
    public Optional<ResponseBody<?>> replayCompleted(String requestId) {
        return transactions.inTransaction(connection -> replayCompleted(
                new TransactionContext(connection), requestId));
    }

    /** Returns a completed response using the caller-owned transaction. */
    public Optional<ResponseBody<?>> replayCompleted(
            TransactionContext context, String requestId) throws Exception {
        return findCompleted(context, requestId);
    }

    /** Claims an unclaimed request inside an existing transaction. */
    public boolean claim(TransactionContext context, Message request) throws Exception {
        requireRequestId(request);
        if (exists(context, request.requestId())) {
            return false;
        }
        String sql = "INSERT INTO tblRequestDedup (requestId, userId, clientInstanceId, command, "
                + "processingStatus, createdAt) VALUES (?, ?, ?, ?, ?, ?)";
        try (var statement = context.connection().prepareStatement(sql)) {
            statement.setString(1, request.requestId());
            statement.setString(2, context.userId());
            statement.setString(3, context.clientInstanceId());
            statement.setString(4, request.command());
            statement.setString(5, "PROCESSING");
            statement.setTimestamp(6, Timestamp.from(Instant.now()));
            return statement.executeUpdate() == 1;
        }
    }

    /** Completes a claimed request inside an existing transaction. */
    public void complete(TransactionContext context, Message response) throws Exception {
        if (!(response.body() instanceof ResponseBody<?> body)) {
            throw new IllegalArgumentException("Response message must contain ResponseBody");
        }
        String sql = "UPDATE tblRequestDedup SET processingStatus = ?, resultCode = ?, "
                + "responseSnapshot = ?, completedAt = ? WHERE requestId = ?";
        try (var statement = context.connection().prepareStatement(sql)) {
            statement.setString(1, "COMPLETED");
            statement.setString(2, body.code());
            statement.setString(3, serialize(body));
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.setString(5, response.requestId());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Request was not claimed: " + response.requestId());
            }
        }
    }

    /** Stores a completed response directly inside a caller-serialized transaction. */
    public void storeCompleted(TransactionContext context, Message request,
            ResponseBody<?> body) throws Exception {
        requireRequestId(request);
        String sql = "INSERT INTO tblRequestDedup (requestId, userId, clientInstanceId, command, "
                + "processingStatus, resultCode, responseSnapshot, createdAt, completedAt) "
                + "VALUES (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        try (var statement = context.connection().prepareStatement(sql)) {
            statement.setString(1, request.requestId());
            statement.setString(2, context.userId());
            statement.setString(3, context.clientInstanceId());
            statement.setString(4, request.command());
            statement.setString(5, body.code());
            statement.setString(6, serialize(body));
            statement.setTimestamp(7, now);
            statement.setTimestamp(8, now);
            statement.executeUpdate();
        }
    }

    private Optional<ResponseBody<?>> findCompleted(
            TransactionContext context, String requestId) throws Exception {
        String sql = "SELECT processingStatus, responseSnapshot FROM tblRequestDedup WHERE requestId = ?";
        try (var statement = context.connection().prepareStatement(sql)) {
            statement.setString(1, requestId);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !"COMPLETED".equals(result.getString(1))) {
                    return Optional.empty();
                }
                return Optional.of(deserialize(result.getString(2)));
            }
        }
    }

    private static boolean exists(TransactionContext context, String requestId) throws Exception {
        try (var statement = context.connection().prepareStatement(
                "SELECT requestId FROM tblRequestDedup WHERE requestId = ?")) {
            statement.setString(1, requestId);
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String serialize(ResponseBody<?> body) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(body);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static ResponseBody<?> deserialize(String snapshot) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(snapshot);
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ResponseBody<?>) input.readObject();
        }
    }

    private static void requireRequestId(Message message) {
        if (message == null || message.requestId() == null || message.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> ResponseBody<T> cast(ResponseBody<?> response) {
        return (ResponseBody<T>) response;
    }
}
