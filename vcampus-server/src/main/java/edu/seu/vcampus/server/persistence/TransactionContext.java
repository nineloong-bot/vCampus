package edu.seu.vcampus.server.persistence;

import java.sql.Connection;
import java.util.Objects;

/** Existing transaction plus diagnostic request identity for coordinated writes. */
public record TransactionContext(Connection connection, String userId, String clientInstanceId) {
    /** Validates a transaction context. */
    public TransactionContext {
        Objects.requireNonNull(connection, "connection");
        clientInstanceId = clientInstanceId == null ? "unknown" : clientInstanceId;
    }

    /** Creates a context when no authenticated or client diagnostic identity is available. */
    public TransactionContext(Connection connection) {
        this(connection, null, "unknown");
    }
}
