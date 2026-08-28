package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;

import java.sql.Connection;
import java.sql.SQLException;

/** Persists catalog titles and their physical copies. */
public interface BookRepository {
    Book insertBook(Connection connection, Book book) throws SQLException;

    Book requireBook(Connection connection, String bookId) throws SQLException;

    BookCopy insertCopy(Connection connection, BookCopy copy) throws SQLException;

    BookCopy requireCopy(Connection connection, String copyId) throws SQLException;

    void updateCopyStatus(Connection connection, String copyId, CopyStatus status,
            long expectedVersion) throws SQLException;
}
