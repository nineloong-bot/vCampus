package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.server.library.domain.LoanPolicy;
import edu.seu.vcampus.common.library.PenaltyPolicy;

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
                        result.getLong("rowVersion"), new PenaltyPolicy(result.getInt("firstTierDays"),
                                result.getInt("secondTierDays"), result.getBigDecimal("firstDailyFine"),
                                result.getBigDecimal("secondDailyFine"), result.getBigDecimal("thirdDailyFine"),
                                result.getBigDecimal("minorDamageFine"), result.getBigDecimal("majorDamageFine"),
                                result.getBigDecimal("lostFine")));
            }
        }
    }

    @Override
    public LoanPolicy update(Connection connection, LoanPolicy policy, long expectedVersion)
            throws SQLException {
        String sql = "UPDATE tblLibraryPolicy SET maxActiveLoans = ?, loanDays = ?, "
                + "maxRenewals = ?, renewalDays = ?, firstTierDays = ?, secondTierDays = ?, "
                + "firstDailyFine = ?, secondDailyFine = ?, thirdDailyFine = ?, minorDamageFine = ?, "
                + "majorDamageFine = ?, lostFine = ?, rowVersion = rowVersion + 1 "
                + "WHERE roleCode = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, policy.maxActiveLoans());
            statement.setInt(2, policy.loanDays());
            statement.setInt(3, policy.maxRenewals());
            statement.setInt(4, policy.renewalDays());
            PenaltyPolicy penalty = policy.penalties();
            statement.setInt(5, penalty.firstTierDays()); statement.setInt(6, penalty.secondTierDays());
            statement.setBigDecimal(7, penalty.firstDailyFine()); statement.setBigDecimal(8, penalty.secondDailyFine());
            statement.setBigDecimal(9, penalty.thirdDailyFine()); statement.setBigDecimal(10, penalty.minorDamageFine());
            statement.setBigDecimal(11, penalty.majorDamageFine()); statement.setBigDecimal(12, penalty.lostFine());
            statement.setString(13, policy.roleCode());
            statement.setLong(14, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException(
                        "Library policy changed: " + policy.roleCode());
            }
            return policy;
        }
    }
}
