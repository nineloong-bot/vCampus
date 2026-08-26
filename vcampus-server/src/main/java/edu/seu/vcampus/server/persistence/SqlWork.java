package edu.seu.vcampus.server.persistence;

import java.sql.Connection;

/** Unit of JDBC work executed inside one transaction. */
@FunctionalInterface
public interface SqlWork<T> {
    /** Performs work without committing or rolling back the supplied connection. */
    T apply(Connection connection) throws Exception;
}
