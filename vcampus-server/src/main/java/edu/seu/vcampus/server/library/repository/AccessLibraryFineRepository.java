package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.LibraryFineQuery;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.paging.PageResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/** Queries library-owned assessed amounts; payment information comes through the wallet port. */
public final class AccessLibraryFineRepository {
    /** Creates a stateless Access fine query repository. */
    public AccessLibraryFineRepository() { }

    /** Lists nonzero finalized fines, optionally restricted to one authenticated borrower. */
    public PageResult<LoanView> find(Connection connection, String userId, LibraryFineQuery query)
            throws SQLException {
        String sql = AccessLoanQueries.SELECT_JOINED
                + " WHERE l.loanStatus IN ('RETURNED','LOST') AND (l.overdueFine + l.damageFine) > 0"
                + (userId == null ? "" : " AND l.borrowerUserId = ?")
                + " ORDER BY l.borrowedAt DESC, l.loanId DESC";
        try (var statement = connection.prepareStatement(sql)) {
            if (userId != null) statement.setString(1, userId);
            var records = AccessLoanQueries.read(statement.executeQuery(), Instant.EPOCH);
            int from = (int) Math.min((long) (query.page() - 1) * query.pageSize(), records.size());
            int to = Math.min(from + query.pageSize(), records.size());
            return new PageResult<>(records.subList(from, to), query.page(), query.pageSize(), records.size());
        }
    }
}
