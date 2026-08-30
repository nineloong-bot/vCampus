package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.AdminLoanSearchQuery;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.Loan;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.List;

/** UCanAccess implementation of loan persistence. */
public final class AccessLoanRepository implements LoanRepository {
    @Override
    public long countEffectiveLoans(Connection connection, String userId, Instant now)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM tblBookLoan WHERE borrowerUserId = ? "
                + "AND loanStatus IN ('ACTIVE', 'OVERDUE')";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    @Override
    public boolean hasOverdueLoan(Connection connection, String userId, Instant now)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM tblBookLoan WHERE borrowerUserId = ? "
                + "AND (loanStatus = 'OVERDUE' OR (loanStatus = 'ACTIVE' AND dueAt < ?))";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1) > 0;
            }
        }
    }

    @Override
    public Loan insert(Connection connection, Loan loan) throws SQLException {
        String sql = "INSERT INTO tblBookLoan (loanId, copyId, borrowerUserId, borrowedAt, dueAt, "
                + "returnedAt, renewCount, loanStatus, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            bindLoan(statement, loan);
            statement.executeUpdate();
            return loan;
        }
    }

    @Override
    public Loan require(Connection connection, String loanId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tblBookLoan WHERE loanId = ?")) {
            statement.setString(1, loanId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NoSuchElementException("Loan not found: " + loanId);
                }
                Timestamp returnedAt = result.getTimestamp("returnedAt");
                return new Loan(result.getString("loanId"), result.getString("copyId"),
                        result.getString("borrowerUserId"), result.getTimestamp("borrowedAt").toInstant(),
                        result.getTimestamp("dueAt").toInstant(),
                        returnedAt == null ? null : returnedAt.toInstant(), result.getInt("renewCount"),
                        LoanStatus.valueOf(result.getString("loanStatus")), result.getLong("rowVersion"));
            }
        }
    }

    @Override
    public void update(Connection connection, Loan loan, long expectedVersion) throws SQLException {
        String sql = "UPDATE tblBookLoan SET copyId = ?, borrowerUserId = ?, borrowedAt = ?, "
                + "dueAt = ?, returnedAt = ?, renewCount = ?, loanStatus = ?, rowVersion = rowVersion + 1 "
                + "WHERE loanId = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, loan.copyId());
            statement.setString(2, loan.borrowerUserId());
            statement.setTimestamp(3, Timestamp.from(loan.borrowedAt()));
            statement.setTimestamp(4, Timestamp.from(loan.dueAt()));
            setNullableTimestamp(statement, 5, loan.returnedAt());
            statement.setInt(6, loan.renewCount());
            statement.setString(7, loan.status().name());
            statement.setString(8, loan.loanId());
            statement.setLong(9, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException("Loan changed: " + loan.loanId());
            }
        }
    }

    @Override
    public int markOverdue(Connection connection, Instant now) throws SQLException {
        String sql = "UPDATE tblBookLoan SET loanStatus = 'OVERDUE', "
                + "rowVersion = rowVersion + 1 WHERE loanStatus = 'ACTIVE' AND dueAt < ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            return statement.executeUpdate();
        }
    }

    @Override
    public List<LoanView> findCurrentForUser(Connection connection, String userId, Instant now)
            throws SQLException {
        return AccessLoanQueries.currentForUser(connection, userId, now);
    }

    @Override
    public PageResult<LoanView> findHistoryForUser(Connection connection, String userId,
            LoanHistoryQuery query, Instant now) throws SQLException {
        return AccessLoanQueries.historyForUser(connection, userId, query, now);
    }

    @Override
    public PageResult<LoanView> searchAll(Connection connection, AdminLoanSearchQuery query,
            Instant now) throws SQLException {
        return AccessLoanQueries.searchAll(connection, query, now);
    }

    private static void bindLoan(java.sql.PreparedStatement statement, Loan loan) throws SQLException {
        statement.setString(1, loan.loanId());
        statement.setString(2, loan.copyId());
        statement.setString(3, loan.borrowerUserId());
        statement.setTimestamp(4, Timestamp.from(loan.borrowedAt()));
        statement.setTimestamp(5, Timestamp.from(loan.dueAt()));
        setNullableTimestamp(statement, 6, loan.returnedAt());
        statement.setInt(7, loan.renewCount());
        statement.setString(8, loan.status().name());
        statement.setLong(9, loan.rowVersion());
    }

    private static void setNullableTimestamp(java.sql.PreparedStatement statement, int index,
            Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }
}
