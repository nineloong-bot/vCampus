package edu.seu.vcampus.server.library.repository;

import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;
import edu.seu.vcampus.server.library.domain.Loan;
import edu.seu.vcampus.server.library.service.DuplicateBarcodeException;
import edu.seu.vcampus.server.library.service.DuplicateIsbnException;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryRepositoryTest {
    private ConnectionProvider connections;
    private BookRepository books;
    private LoanRepository loans;
    private LibraryPolicyRepository policies;

    @BeforeEach
    void createDatabase() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                .toAbsolutePath() + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        connections = () -> DriverManager.getConnection(url);
        try (Connection connection = connections.open()) {
            executeScript(connection, Path.of("..", "vcampus-database", "schema", "010_user.sql"));
            executeScript(connection, Path.of("..", "vcampus-database", "schema", "040_library.sql"));
            executeScript(connection, Path.of("..", "vcampus-database", "seed", "040_library_policy.sql"));
        }
        books = new AccessBookRepository();
        loans = new AccessLoanRepository();
        policies = new AccessLibraryPolicyRepository();
    }

    @Test
    void enforcesIsbnAndBarcodeUniqueness() throws Exception {
        try (Connection connection = connections.open()) {
            books.insertBook(connection, book("book-1", "9787300000001"));
            books.insertCopy(connection, copy("copy-1", "book-1", "BC-001"));

            assertThatThrownBy(() -> books.insertBook(connection,
                    book("book-2", "9787300000001"))).isInstanceOf(DuplicateIsbnException.class);
            assertThatThrownBy(() -> books.insertCopy(connection,
                    copy("copy-2", "book-1", "BC-001"))).isInstanceOf(DuplicateBarcodeException.class);
        }
    }

    @Test
    void loadsDifferentPoliciesForStudentAndTeacher() throws Exception {
        try (Connection connection = connections.open()) {
            assertThat(policies.require(connection, "STUDENT"))
                    .extracting(policy -> policy.maxActiveLoans(), policy -> policy.loanDays())
                    .containsExactly(5, 30);
            assertThat(policies.require(connection, "TEACHER"))
                    .extracting(policy -> policy.maxActiveLoans(), policy -> policy.loanDays())
                    .containsExactly(10, 60);
        }
    }

    @Test
    void countsActiveAndDynamicallyOverdueLoans() throws Exception {
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        try (Connection connection = connections.open()) {
            seedBookAndCopies(connection, 3);
            loans.insert(connection, loan("loan-active", "copy-1", "user-1",
                    now.plus(1, ChronoUnit.DAYS), LoanStatus.ACTIVE));
            loans.insert(connection, loan("loan-due", "copy-2", "user-1",
                    now.minus(1, ChronoUnit.DAYS), LoanStatus.ACTIVE));
            loans.insert(connection, loan("loan-returned", "copy-3", "user-1",
                    now.minus(2, ChronoUnit.DAYS), LoanStatus.RETURNED));

            assertThat(loans.countEffectiveLoans(connection, "user-1", now)).isEqualTo(2);
            assertThat(loans.hasOverdueLoan(connection, "user-1", now)).isTrue();
        }
    }

    @Test
    void rejectsStaleCopyAndLoanVersions() throws Exception {
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        try (Connection connection = connections.open()) {
            seedBookAndCopies(connection, 1);
            books.updateCopyStatus(connection, "copy-1", CopyStatus.BORROWED, 0);
            assertThat(books.requireCopy(connection, "copy-1").rowVersion()).isEqualTo(1);
            assertThatThrownBy(() -> books.updateCopyStatus(connection, "copy-1",
                    CopyStatus.AVAILABLE, 0)).isInstanceOf(java.util.ConcurrentModificationException.class);

            Loan inserted = loans.insert(connection, loan("loan-1", "copy-1", "user-1",
                    now.plus(30, ChronoUnit.DAYS), LoanStatus.ACTIVE));
            Loan renewed = new Loan(inserted.loanId(), inserted.copyId(), inserted.borrowerUserId(),
                    inserted.borrowedAt(), inserted.dueAt().plus(15, ChronoUnit.DAYS), null,
                    1, LoanStatus.ACTIVE, inserted.rowVersion());
            loans.update(connection, renewed, 0);
            assertThat(loans.require(connection, "loan-1").rowVersion()).isEqualTo(1);
            assertThatThrownBy(() -> loans.update(connection, renewed, 0))
                    .isInstanceOf(java.util.ConcurrentModificationException.class);
        }
    }

    private void seedBookAndCopies(Connection connection, int count) throws SQLException {
        books.insertBook(connection, book("book-1", "9787300000001"));
        for (int index = 1; index <= count; index++) {
            books.insertCopy(connection, copy("copy-" + index, "book-1", "BC-00" + index));
        }
    }

    private static Book book(String id, String isbn) {
        return new Book(id, isbn, "Java 21", "Author", "SEU Press",
                LocalDate.of(2026, 8, 24), "COMPUTER", "Test book", true, 0);
    }

    private static BookCopy copy(String id, String bookId, String barcode) {
        return new BookCopy(id, bookId, barcode, "LIB-A-01", CopyStatus.AVAILABLE, 0);
    }

    private static Loan loan(String id, String copyId, String userId, Instant dueAt,
            LoanStatus status) {
        return new Loan(id, copyId, userId, dueAt.minus(30, ChronoUnit.DAYS), dueAt,
                status == LoanStatus.RETURNED ? dueAt.minus(1, ChronoUnit.DAYS) : null,
                0, status, 0);
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
