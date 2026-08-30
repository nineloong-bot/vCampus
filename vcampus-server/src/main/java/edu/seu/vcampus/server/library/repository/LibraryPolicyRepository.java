package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.server.library.domain.LoanPolicy;

import java.sql.Connection;
import java.sql.SQLException;

/** Loads role-specific borrowing limits. */
public interface LibraryPolicyRepository {
    LoanPolicy require(Connection connection, String roleCode) throws SQLException;

    LoanPolicy update(Connection connection, LoanPolicy policy, long expectedVersion)
            throws SQLException;
}
