package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;
import edu.seu.vcampus.server.library.domain.Loan;
import edu.seu.vcampus.server.library.repository.AccessBookRepository;
import edu.seu.vcampus.server.library.repository.AccessLibraryPolicyRepository;
import edu.seu.vcampus.server.library.repository.AccessLoanRepository;
import edu.seu.vcampus.server.library.repository.BookRepository;
import edu.seu.vcampus.server.library.repository.LibraryPolicyRepository;
import edu.seu.vcampus.server.library.repository.LoanRepository;
import edu.seu.vcampus.server.persistence.ConnectionProvider;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class LibraryServiceFixture {
    static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");

    final BookRepository books = new AccessBookRepository();
    final LoanRepository loans = new AccessLoanRepository();
    final LibraryPolicyRepository policies = new AccessLibraryPolicyRepository();
    final Map<String, BorrowerIdentity> identities = new ConcurrentHashMap<>();
    final ConnectionProvider connections;
    final TransactionManager transactions;

    LibraryServiceFixture() throws Exception {
        Path testData = Path.of("target", "test-data");
        Files.createDirectories(testData);
        String url = "jdbc:ucanaccess://" + testData.resolve(UUID.randomUUID() + ".accdb")
                .toAbsolutePath() + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true";
        connections = () -> DriverManager.getConnection(url);
        transactions = new TransactionManager(connections);
        try (Connection connection = connections.open()) {
            executeScript(connection, Path.of("..", "vcampus-database", "schema", "040_library.sql"));
            executeScript(connection, Path.of("..", "vcampus-database", "seed", "040_library_policy.sql"));
        }
    }

    LibraryService service() {
        return new LibraryServiceImpl(token -> {
            BorrowerIdentity identity = identities.get(token);
            if (identity == null) {
                throw new IllegalArgumentException("Unknown test token");
            }
            return identity;
        }, books, loans, policies, transactions, new StripedResourceLockManager(),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> UUID.randomUUID().toString());
    }

    void addIdentity(String token, String userId, String roleCode) {
        identities.put(token, new BorrowerIdentity(userId, roleCode));
    }

    void seedCopies(int count) throws Exception {
        try (Connection connection = connections.open()) {
            books.insertBook(connection, new Book("book-1", "9787300000001", "Java 21",
                    "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER",
                    "Test book", true, 0));
            for (int index = 1; index <= count; index++) {
                books.insertCopy(connection, new BookCopy("copy-" + index, "book-1",
                        "BC-" + index, "LIB-A-01", CopyStatus.AVAILABLE, 0));
            }
        }
    }

    Loan seedOverdue(String copyId, String userId) throws Exception {
        try (Connection connection = connections.open()) {
            BookCopy copy = books.requireCopy(connection, copyId);
            books.updateCopyStatus(connection, copyId, CopyStatus.BORROWED, copy.rowVersion());
            return loans.insert(connection, new Loan(UUID.randomUUID().toString(), copyId, userId,
                    NOW.minus(31, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS), null,
                    0, LoanStatus.ACTIVE, 0));
        }
    }

    long loanCountForCopy(String copyId) throws Exception {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM tblBookLoan WHERE copyId = ?")) {
            statement.setString(1, copyId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
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
