package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.BookDetail;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/** UCanAccess implementation of catalog persistence. */
public final class AccessBookRepository implements BookRepository {
    @Override
    public Book insertBook(Connection connection, Book book) throws SQLException {
        String sql = "INSERT INTO tblBook (bookId, isbn, title, author, publisher, publishDate, "
                + "category, description, isActive, rowVersion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, book.bookId());
            statement.setString(2, book.isbn());
            statement.setString(3, book.title());
            statement.setString(4, book.author());
            statement.setString(5, book.publisher());
            statement.setDate(6, book.publishDate() == null ? null : Date.valueOf(book.publishDate()));
            statement.setString(7, book.category());
            statement.setString(8, book.description());
            statement.setBoolean(9, book.active());
            statement.setLong(10, book.rowVersion());
            statement.executeUpdate();
            return book;
        }
    }

    @Override
    public Book requireBook(Connection connection, String bookId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM tblBook WHERE bookId = ?")) {
            statement.setString(1, bookId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NoSuchElementException("Book not found: " + bookId);
                }
                Date publishDate = result.getDate("publishDate");
                return new Book(result.getString("bookId"), result.getString("isbn"),
                        result.getString("title"), result.getString("author"),
                        result.getString("publisher"), publishDate == null ? null : publishDate.toLocalDate(),
                        result.getString("category"), result.getString("description"),
                        result.getBoolean("isActive"), result.getLong("rowVersion"));
            }
        }
    }

    @Override
    public PageResult<BookSummary> search(Connection connection, BookSearchQuery query)
            throws SQLException {
        return AccessCatalogQueries.search(connection, query);
    }

    @Override
    public BookDetail requireDetail(Connection connection, String bookId) throws SQLException {
        return AccessCatalogQueries.detail(requireBook(connection, bookId), connection);
    }

    @Override
    public void updateBook(Connection connection, Book book, long expectedVersion)
            throws SQLException {
        String sql = "UPDATE tblBook SET isbn = ?, title = ?, author = ?, publisher = ?, "
                + "publishDate = ?, category = ?, description = ?, isActive = ?, "
                + "rowVersion = rowVersion + 1 WHERE bookId = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, book.isbn());
            statement.setString(2, book.title());
            statement.setString(3, book.author());
            statement.setString(4, book.publisher());
            statement.setDate(5, book.publishDate() == null ? null : Date.valueOf(book.publishDate()));
            statement.setString(6, book.category());
            statement.setString(7, book.description());
            statement.setBoolean(8, book.active());
            statement.setString(9, book.bookId());
            statement.setLong(10, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException("Book changed: " + book.bookId());
            }
        }
    }

    @Override
    public BookCopy insertCopy(Connection connection, BookCopy copy) throws SQLException {
        String sql = "INSERT INTO tblBookCopy (copyId, bookId, barcode, locationCode, copyStatus, "
                + "rowVersion) VALUES (?, ?, ?, ?, ?, ?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, copy.copyId());
            statement.setString(2, copy.bookId());
            statement.setString(3, copy.barcode());
            statement.setString(4, copy.locationCode());
            statement.setString(5, copy.status().name());
            statement.setLong(6, copy.rowVersion());
            statement.executeUpdate();
            return copy;
        }
    }

    @Override
    public BookCopy requireCopy(Connection connection, String copyId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM tblBookCopy WHERE copyId = ?")) {
            statement.setString(1, copyId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new NoSuchElementException("Book copy not found: " + copyId);
                }
                return new BookCopy(result.getString("copyId"), result.getString("bookId"),
                        result.getString("barcode"), result.getString("locationCode"),
                        CopyStatus.valueOf(result.getString("copyStatus")),
                        result.getLong("rowVersion"));
            }
        }
    }

    @Override
    public void updateCopyStatus(Connection connection, String copyId, CopyStatus status,
            long expectedVersion) throws SQLException {
        String sql = "UPDATE tblBookCopy SET copyStatus = ?, rowVersion = rowVersion + 1 "
                + "WHERE copyId = ? AND rowVersion = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, copyId);
            statement.setLong(3, expectedVersion);
            if (statement.executeUpdate() != 1) {
                throw new ConcurrentModificationException("Book copy changed: " + copyId);
            }
        }
    }
}
