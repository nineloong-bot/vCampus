package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.server.library.domain.LoanPolicy;

import java.sql.Connection;
import java.sql.SQLException;

/** Loads role-specific borrowing limits. */
public interface LibraryPolicyRepository {
    /**
     * Performs the require operation.
     * @param connection the connection
     * @param roleCode the role code
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    LoanPolicy require(Connection connection, String roleCode) throws SQLException;

    /**
     * Performs the update operation.
     * @param connection the connection
     * @param policy the policy
     * @param expectedVersion the expected version
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    LoanPolicy update(Connection connection, LoanPolicy policy, long expectedVersion)
            throws SQLException;
}
