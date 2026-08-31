package edu.seu.vcampus.server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Executes JDBC work with commit-on-success and rollback-on-failure semantics. */
public final class TransactionManager {
    private final ConnectionProvider provider;

    /** Creates a transaction manager backed by the given connection provider. */
    public TransactionManager(ConnectionProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /** Executes one unit of work in a new transaction. */
    public synchronized <T> T inTransaction(SqlWork<T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = provider.open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (Exception error) {
                rollback(connection, error);
                throw translate(error);
            }
        } catch (SQLException error) {
            throw new PersistenceException("Database transaction failed", error);
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }

    private static RuntimeException translate(Exception error) {
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new PersistenceException("Database transaction failed", error);
    }
}
