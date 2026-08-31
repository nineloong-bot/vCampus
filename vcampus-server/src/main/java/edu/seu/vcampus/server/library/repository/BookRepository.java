package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.BookDetail;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;

import java.sql.Connection;
import java.sql.SQLException;

/** Persists catalog titles and their physical copies. */
public interface BookRepository {
    Book insertBook(Connection connection, Book book) throws SQLException;

    Book requireBook(Connection connection, String bookId) throws SQLException;

    PageResult<BookSummary> search(Connection connection, BookSearchQuery query) throws SQLException;

    PageResult<BookSummary> searchManaged(Connection connection, BookSearchQuery query)
            throws SQLException;

    BookDetail requireDetail(Connection connection, String bookId) throws SQLException;

    void updateBook(Connection connection, Book book, long expectedVersion) throws SQLException;

    BookCopy insertCopy(Connection connection, BookCopy copy) throws SQLException;

    BookCopy requireCopy(Connection connection, String copyId) throws SQLException;

    void updateCopyStatus(Connection connection, String copyId, CopyStatus status,
            long expectedVersion) throws SQLException;
}
