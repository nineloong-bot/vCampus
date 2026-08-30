package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.server.library.domain.LoanPolicy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/** UCanAccess implementation of role-based policy lookup. */
public final class AccessLibraryPolicyRepository implements LibraryPolicyRepository {
    @Override
    public LoanPolicy require(Connection connection, String roleCode) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tblLibraryPolicy WHERE roleCode = ?")) {
            statement.setString(1, roleCode);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NoSuchElementException("Library policy not found: " + roleCode);
                }
                return new LoanPolicy(result.getString("policyId"), result.getString("roleCode"),
                        result.getInt("maxActiveLoans"), result.getInt("loanDays"),
                        result.getInt("maxRenewals"), result.getInt("renewalDays"),
                        result.getLong("rowVersion"));
            }
        }
    }

    @Override
    public LoanPolicy update(Connection connection, LoanPolicy policy, long expectedVersion)
            throws SQLException {
        String sql = "UPDATE tblLibraryPolicy SET maxActiveLoans = ?, loanDays = ?, "
                + "maxRenewals = ?, renewalDays = ?, rowVersion = rowVersion + 1 "
                + "WHERE roleCode = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, policy.maxActiveLoans());
            statement.setInt(2, policy.loanDays());
            statement.setInt(3, policy.maxRenewals());
            statement.setInt(4, policy.renewalDays());
            statement.setString(5, policy.roleCode());
            statement.setLong(6, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException(
                        "Library policy changed: " + policy.roleCode());
            }
            return policy;
        }
    }
}
