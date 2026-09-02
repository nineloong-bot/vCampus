package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.BookCopyView;
import edu.seu.vcampus.common.library.BookDetail;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSearchField;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.Book;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class AccessCatalogQueries {
    private AccessCatalogQueries() {
    }

    static PageResult<BookSummary> search(Connection connection, BookSearchQuery query,
            boolean includeInactive)
            throws SQLException {
        requirePage(query.page(), query.pageSize());
        StringBuilder sql = new StringBuilder("SELECT * FROM tblBook WHERE 1 = 1");
        if (!includeInactive) sql.append(" AND isActive = TRUE");
        List<String> values = new ArrayList<>();
        if (hasText(query.keyword())) {
            String pattern = "%" + query.keyword().trim() + "%";
            BookSearchField field = query.field() == null ? BookSearchField.ANY : query.field();
            if (field == BookSearchField.ANY) {
                sql.append(" AND (title LIKE ? OR author LIKE ? OR isbn LIKE ?"
                        + " OR category LIKE ? OR publisher LIKE ?)");
                for (int index = 0; index < 5; index++) values.add(pattern);
            } else {
                String column = switch (field) {
                    case TITLE -> "title";
                    case AUTHOR -> "author";
                    case ISBN -> "isbn";
                    case CATEGORY -> "category";
                    case PUBLISHER -> "publisher";
                    case ANY -> throw new IllegalStateException("ANY handled above");
                };
                sql.append(" AND ").append(column).append(" LIKE ?");
                values.add(pattern);
            }
        }
        if (hasText(query.category())) {
            sql.append(" AND category = ?");
            values.add(query.category().trim());
        }
        sql.append(" ORDER BY title");
        List<BookSummary> matches = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < values.size(); index++) {
                statement.setString(index + 1, values.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String bookId = result.getString("bookId");
                    int total = countCopies(connection, bookId, null);
                    int available = countCopies(connection, bookId, CopyStatus.AVAILABLE);
                    if (!Boolean.TRUE.equals(query.availableOnly()) || available > 0) {
                        matches.add(new BookSummary(bookId, result.getString("isbn"),
                                result.getString("title"), result.getString("author"),
                                result.getString("category"), available, total,
                                result.getBoolean("isActive")));
                    }
                }
            }
        }
        int from = Math.min((query.page() - 1) * query.pageSize(), matches.size());
        int to = Math.min(from + query.pageSize(), matches.size());
        return new PageResult<>(matches.subList(from, to), query.page(), query.pageSize(),
                matches.size());
    }

    static BookDetail detail(Book book, Connection connection) throws SQLException {
        List<BookCopyView> copies = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tblBookCopy WHERE bookId = ? ORDER BY barcode")) {
            statement.setString(1, book.bookId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    copies.add(new BookCopyView(result.getString("copyId"), book.bookId(),
                            result.getString("barcode"), result.getString("locationCode"),
                            CopyStatus.valueOf(result.getString("copyStatus")),
                            result.getLong("rowVersion")));
                }
            }
        }
        return new BookDetail(book.bookId(), book.isbn(), book.title(), book.author(),
                book.publisher(), book.publishDate(), book.category(), book.description(),
                book.active(), book.rowVersion(), copies);
    }

    private static int countCopies(Connection connection, String bookId, CopyStatus status)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM tblBookCopy WHERE bookId = ?"
                + (status == null ? "" : " AND copyStatus = ?");
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            if (status != null) {
                statement.setString(2, status.name());
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Page and page size must be positive");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
