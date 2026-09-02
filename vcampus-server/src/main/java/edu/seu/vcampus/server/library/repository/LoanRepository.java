package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.AdminLoanSearchQuery;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.Loan;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Persists loans and evaluates active/overdue borrowing state. */
public interface LoanRepository {
    long countEffectiveLoans(Connection connection, String userId, Instant now) throws SQLException;

    boolean hasOverdueLoan(Connection connection, String userId, Instant now) throws SQLException;

    boolean hasEffectiveLoanForCopy(Connection connection, String copyId) throws SQLException;

    Loan insert(Connection connection, Loan loan) throws SQLException;

    Loan require(Connection connection, String loanId) throws SQLException;

    void update(Connection connection, Loan loan, long expectedVersion) throws SQLException;

    int markOverdue(Connection connection, Instant now) throws SQLException;

    List<LoanView> findCurrentForUser(Connection connection, String userId, Instant now)
            throws SQLException;

    PageResult<LoanView> findHistoryForUser(Connection connection, String userId,
            LoanHistoryQuery query, Instant now) throws SQLException;

    PageResult<LoanView> searchAll(Connection connection, AdminLoanSearchQuery query,
            Instant now) throws SQLException;
}
