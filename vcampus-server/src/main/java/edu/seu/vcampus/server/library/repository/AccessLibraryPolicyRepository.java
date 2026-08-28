package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.server.library.domain.LoanPolicy;

import java.sql.Connection;
import java.sql.SQLException;
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
}
