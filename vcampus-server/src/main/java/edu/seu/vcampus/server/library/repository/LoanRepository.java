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
    /**
     * Performs the count effective loans operation.
     * @param connection the connection
     * @param userId the user identifier
     * @param now the now
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    long countEffectiveLoans(Connection connection, String userId, Instant now) throws SQLException;

    /**
     * Performs the has overdue loan operation.
     * @param connection the connection
     * @param userId the user identifier
     * @param now the now
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    boolean hasOverdueLoan(Connection connection, String userId, Instant now) throws SQLException;

    /**
     * Performs the has effective loan for copy operation.
     * @param connection the connection
     * @param copyId the copy identifier
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    boolean hasEffectiveLoanForCopy(Connection connection, String copyId) throws SQLException;

    /**
     * Performs the insert operation.
     * @param connection the connection
     * @param loan the loan
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    Loan insert(Connection connection, Loan loan) throws SQLException;

    /**
     * Performs the require operation.
     * @param connection the connection
     * @param loanId the loan identifier
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    Loan require(Connection connection, String loanId) throws SQLException;

    /**
     * Performs the update operation.
     * @param connection the connection
     * @param loan the loan
     * @param expectedVersion the expected version
     * @throws SQLException when the operation cannot be completed
     */
    void update(Connection connection, Loan loan, long expectedVersion) throws SQLException;

    /**
     * Performs the mark overdue operation.
     * @param connection the connection
     * @param now the now
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    int markOverdue(Connection connection, Instant now) throws SQLException;

    /**
     * Performs the find current for user operation.
     * @param connection the connection
     * @param userId the user identifier
     * @param now the now
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    List<LoanView> findCurrentForUser(Connection connection, String userId, Instant now)
            throws SQLException;

    /**
     * Performs the find history for user operation.
     * @param connection the connection
     * @param userId the user identifier
     * @param query the query
     * @param now the now
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    PageResult<LoanView> findHistoryForUser(Connection connection, String userId,
            LoanHistoryQuery query, Instant now) throws SQLException;

    /**
     * Performs the search all operation.
     * @param connection the connection
     * @param query the query
     * @param now the now
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    PageResult<LoanView> searchAll(Connection connection, AdminLoanSearchQuery query,
            Instant now) throws SQLException;
}
