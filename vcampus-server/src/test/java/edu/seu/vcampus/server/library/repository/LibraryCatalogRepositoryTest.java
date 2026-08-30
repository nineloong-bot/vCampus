package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;
import edu.seu.vcampus.server.library.domain.LoanPolicy;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryCatalogRepositoryTest {
    private ConnectionProvider connections;
    private BookRepository books;
    private LibraryPolicyRepository policies;

    @BeforeEach
    void setUp() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                .toAbsolutePath() + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        connections = () -> DriverManager.getConnection(url);
        try (Connection connection = connections.open()) {
            executeScript(connection, Path.of("..", "vcampus-database", "schema", "040_library.sql"));
            executeScript(connection, Path.of("..", "vcampus-database", "seed", "040_library_policy.sql"));
        }
        books = new AccessBookRepository();
        policies = new AccessLibraryPolicyRepository();
    }

    @Test
    void searchesAvailableBooksAndLoadsCopyDetail() throws Exception {
        try (Connection connection = connections.open()) {
            books.insertBook(connection, book("book-1", "9787300000001", "Java 21"));
            books.insertBook(connection, book("book-2", "9787300000002", "Java 并发"));
            books.insertCopy(connection, copy("copy-1", "book-1", "BC-1"));
            books.insertCopy(connection, copy("copy-2", "book-2", "BC-2"));
            books.updateCopyStatus(connection, "copy-2", CopyStatus.BORROWED, 0);

            var result = books.search(connection,
                    new BookSearchQuery("Java", "COMPUTER", true, 1, 20));

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.items()).singleElement().satisfies(summary -> {
                assertThat(summary.bookId()).isEqualTo("book-1");
                assertThat(summary.availableCopies()).isEqualTo(1);
            });
            assertThat(books.requireDetail(connection, "book-1").copies())
                    .singleElement().extracting(copy -> copy.barcode()).isEqualTo("BC-1");
        }
    }

    @Test
    void updatesBookAndPolicyWithOptimisticVersions() throws Exception {
        try (Connection connection = connections.open()) {
            Book inserted = books.insertBook(connection, book("book-1", "9787300000001", "Old"));
            Book changed = new Book(inserted.bookId(), inserted.isbn(), "New", inserted.author(),
                    inserted.publisher(), inserted.publishDate(), inserted.category(),
                    inserted.description(), true, 1);
            books.updateBook(connection, changed, 0);
            assertThat(books.requireBook(connection, "book-1").title()).isEqualTo("New");
            assertThatThrownBy(() -> books.updateBook(connection, changed, 0))
                    .isInstanceOf(java.util.ConcurrentModificationException.class);

            LoanPolicy student = policies.require(connection, "STUDENT");
            LoanPolicy changedPolicy = new LoanPolicy(student.policyId(), student.roleCode(),
                    6, 31, 2, 16, 1);
            policies.update(connection, changedPolicy, 0);
            assertThat(policies.require(connection, "STUDENT").maxActiveLoans()).isEqualTo(6);
            assertThatThrownBy(() -> policies.update(connection, changedPolicy, 0))
                    .isInstanceOf(java.util.ConcurrentModificationException.class);
        }
    }

    private static Book book(String id, String isbn, String title) {
        return new Book(id, isbn, title, "Author", "SEU Press",
                LocalDate.of(2026, 8, 24), "COMPUTER", "Test", true, 0);
    }

    private static BookCopy copy(String id, String bookId, String barcode) {
        return new BookCopy(id, bookId, barcode, "LIB-A-01", CopyStatus.AVAILABLE, 0);
    }

    private static void executeScript(Connection connection, Path path) throws Exception {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) {
                try (var jdbc = connection.createStatement()) {
                    jdbc.execute(statement.trim());
                }
            }
        }
    }
}
