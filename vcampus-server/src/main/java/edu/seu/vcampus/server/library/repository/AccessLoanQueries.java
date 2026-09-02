package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.AdminLoanSearchQuery;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.paging.PageResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class AccessLoanQueries {
    private static final String SELECT_JOINED = "SELECT l.*, c.bookId, c.barcode, b.title, "
            + "u.loginId FROM ((tblBookLoan l INNER JOIN tblBookCopy c ON l.copyId = c.copyId) "
            + "INNER JOIN tblBook b ON c.bookId = b.bookId) "
            + "LEFT JOIN tblUser u ON l.borrowerUserId = u.userId";

    private AccessLoanQueries() {
    }

    static List<LoanView> currentForUser(Connection connection, String userId, Instant now)
            throws SQLException {
        String sql = SELECT_JOINED + " WHERE l.borrowerUserId = ? "
                + "AND l.loanStatus IN ('ACTIVE', 'OVERDUE') ORDER BY l.dueAt";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            return read(statement.executeQuery(), now);
        }
    }

    static PageResult<LoanView> historyForUser(Connection connection, String userId,
            LoanHistoryQuery query, Instant now) throws SQLException {
        requirePage(query.page(), query.pageSize());
        String sql = SELECT_JOINED + " WHERE l.borrowerUserId = ? ORDER BY l.borrowedAt DESC";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            List<LoanView> records = read(statement.executeQuery(), now).stream()
                    .filter(view -> query.status() == null || view.status() == query.status())
                    .toList();
            return page(records, query.page(), query.pageSize());
        }
    }

    static PageResult<LoanView> searchAll(Connection connection, AdminLoanSearchQuery query,
            Instant now) throws SQLException {
        requirePage(query.page(), query.pageSize());
        StringBuilder sql = new StringBuilder(SELECT_JOINED).append(" WHERE 1 = 1");
        List<String> values = new ArrayList<>();
        if (query.borrowerUserId() != null && !query.borrowerUserId().isBlank()) {
            sql.append(" AND (l.borrowerUserId = ? OR u.loginId = ?)");
            String borrower = query.borrowerUserId().trim();
            values.add(borrower);
            values.add(borrower.toUpperCase(java.util.Locale.ROOT));
        }
        sql.append(" ORDER BY l.borrowedAt DESC");
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            List<LoanView> records = read(statement.executeQuery(), now).stream()
                    .filter(view -> query.status() == null || view.status() == query.status())
                    .toList();
            return page(records, query.page(), query.pageSize());
        }
    }

    private static List<LoanView> read(ResultSet result, Instant now) throws SQLException {
        try (result) {
            List<LoanView> records = new ArrayList<>();
            while (result.next()) {
                Instant dueAt = result.getTimestamp("dueAt").toInstant();
                LoanStatus status = LoanStatus.valueOf(result.getString("loanStatus"));
                if (status == LoanStatus.ACTIVE && dueAt.isBefore(now)) {
                    status = LoanStatus.OVERDUE;
                }
                Timestamp returnedAt = result.getTimestamp("returnedAt");
                records.add(new LoanView(result.getString("loanId"), result.getString("copyId"),
                        result.getString("bookId"), result.getString("borrowerUserId"),
                        result.getTimestamp("borrowedAt").toInstant(), dueAt,
                        returnedAt == null ? null : returnedAt.toInstant(),
                        result.getInt("renewCount"), status, result.getLong("rowVersion"),
                        result.getString("loginId"), result.getString("title"),
                        result.getString("barcode")));
            }
            return records;
        }
    }

    private static PageResult<LoanView> page(List<LoanView> records, int page, int pageSize) {
        int from = Math.min((page - 1) * pageSize, records.size());
        int to = Math.min(from + pageSize, records.size());
        return new PageResult<>(records.subList(from, to), page, pageSize, records.size());
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Page and page size must be positive");
        }
    }
}
