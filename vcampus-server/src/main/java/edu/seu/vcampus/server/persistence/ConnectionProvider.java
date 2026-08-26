package edu.seu.vcampus.server.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** Opens JDBC connections for server persistence services. */
@FunctionalInterface
public interface ConnectionProvider {
    /** Opens a new connection owned by the caller. */
    Connection open() throws SQLException;
}
