package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.server.library.domain.Loan;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/** Persists loans and evaluates active/overdue borrowing state. */
public interface LoanRepository {
    long countEffectiveLoans(Connection connection, String userId, Instant now) throws SQLException;

    boolean hasOverdueLoan(Connection connection, String userId, Instant now) throws SQLException;

    Loan insert(Connection connection, Loan loan) throws SQLException;

    Loan require(Connection connection, String loanId) throws SQLException;

    void update(Connection connection, Loan loan, long expectedVersion) throws SQLException;
}
