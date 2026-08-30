package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.server.library.repository.BookRepository;
import edu.seu.vcampus.server.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryBorrowServiceTest {
    private LibraryServiceFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LibraryServiceFixture();
        fixture.seedCopies(2);
        fixture.addIdentity("token", "user-1", "STUDENT");
    }

    @Test
    void dynamicallyOverdueLoanBlocksBorrowing() throws Exception {
        fixture.seedOverdue("copy-1", "user-1");

        assertThatThrownBy(() -> fixture.service().borrow(
                "token", new BorrowBookCommand("copy-2")))
                .isInstanceOf(UserHasOverdueLoansException.class);
        assertThat(fixture.loanCountForCopy("copy-2")).isZero();
    }

    @Test
    void copyUpdateFailureRollsBackInsertedLoan() throws Exception {
        BookRepository failingBooks = (BookRepository) Proxy.newProxyInstance(
                BookRepository.class.getClassLoader(), new Class<?>[] {BookRepository.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("updateCopyStatus")) {
                        throw new SQLException("simulated update failure");
                    }
                    return method.invoke(fixture.books, arguments);
                });
        LibraryService service = new LibraryServiceImpl(token -> fixture.identities.get(token),
                failingBooks, fixture.loans, fixture.policies, fixture.transactions,
                new edu.seu.vcampus.server.concurrency.StripedResourceLockManager(),
                java.time.Clock.fixed(LibraryServiceFixture.NOW, java.time.ZoneOffset.UTC),
                () -> "loan-failure");

        assertThatThrownBy(() -> service.borrow("token", new BorrowBookCommand("copy-1")))
                .isInstanceOf(PersistenceException.class);
        assertThat(fixture.loanCountForCopy("copy-1")).isZero();
        try (Connection connection = fixture.connections.open()) {
            assertThat(fixture.books.requireCopy(connection, "copy-1").status().name())
                    .isEqualTo("AVAILABLE");
        }
    }
}
