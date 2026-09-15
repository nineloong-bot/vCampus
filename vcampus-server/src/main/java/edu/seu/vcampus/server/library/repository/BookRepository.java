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
    /**
     * Performs the insert book operation.
     * @param connection the connection
     * @param book the book
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    Book insertBook(Connection connection, Book book) throws SQLException;

    /**
     * Performs the require book operation.
     * @param connection the connection
     * @param bookId the book identifier
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    Book requireBook(Connection connection, String bookId) throws SQLException;

    /**
     * Performs the search operation.
     * @param connection the connection
     * @param query the query
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    PageResult<BookSummary> search(Connection connection, BookSearchQuery query) throws SQLException;

    /**
     * Performs the search managed operation.
     * @param connection the connection
     * @param query the query
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    PageResult<BookSummary> searchManaged(Connection connection, BookSearchQuery query)
            throws SQLException;

    /**
     * Performs the require detail operation.
     * @param connection the connection
     * @param bookId the book identifier
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    BookDetail requireDetail(Connection connection, String bookId) throws SQLException;

    /**
     * Performs the update book operation.
     * @param connection the connection
     * @param book the book
     * @param expectedVersion the expected version
     * @throws SQLException when the operation cannot be completed
     */
    void updateBook(Connection connection, Book book, long expectedVersion) throws SQLException;

    /**
     * Performs the insert copy operation.
     * @param connection the connection
     * @param copy the copy
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    BookCopy insertCopy(Connection connection, BookCopy copy) throws SQLException;

    /**
     * Performs the require copy operation.
     * @param connection the connection
     * @param copyId the copy identifier
     * @return the operation result
     * @throws SQLException when the operation cannot be completed
     */
    BookCopy requireCopy(Connection connection, String copyId) throws SQLException;

    /**
     * Performs the update copy status operation.
     * @param connection the connection
     * @param copyId the copy identifier
     * @param status the status
     * @param expectedVersion the expected version
     * @throws SQLException when the operation cannot be completed
     */
    void updateCopyStatus(Connection connection, String copyId, CopyStatus status,
            long expectedVersion) throws SQLException;
}
