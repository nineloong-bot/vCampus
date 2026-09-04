package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.AddBookCopyCommand;
import edu.seu.vcampus.common.library.AdminResolveLoanCommand;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.ChangeCopyStatusCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.CreateBookCommand;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.UpdateBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LibraryCatalogServiceTest {
    private LibraryServiceFixture fixture;
    private LibraryService service;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new LibraryServiceFixture();
        fixture.seedCopies(1);
        fixture.addIdentity("token", "user-1", "STUDENT");
        service = fixture.service();
    }

    @Test
    void createsFirstAvailableCopyAtRequestedLocation() {
        var created = service.createBook(new CreateBookCommand("9787300000002", "Algorithms",
                "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER", "Intro", " A-02 ", " LIB-CUSTOM-01 "));

        assertThat(service.getBook(created.bookId()).copies()).singleElement().satisfies(copy -> {
            assertThat(copy.bookId()).isEqualTo(created.bookId());
            assertThat(copy.locationCode()).isEqualTo("A-02");
            assertThat(copy.status()).isEqualTo(CopyStatus.AVAILABLE);
            assertThat(copy.barcode()).isEqualTo("LIB-CUSTOM-01");
        });
    }

    @Test
    void failedFirstCopyInsertRollsBackNewCatalogEntry() {
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        var operations = new LibraryReadAdminOperations(fixture.identities::get, fixture.books,
                fixture.loans, fixture.policies, fixture.transactions, java.time.Clock.systemUTC(),
                () -> sequence.getAndIncrement() == 0 ? "new-book" : "copy-1");

        assertThatThrownBy(() -> operations.createBook(new CreateBookCommand("9787300000002", "Algorithms",
                "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER", "Intro", "A-02", "BC-NEW")))
                .isInstanceOf(RuntimeException.class);

        assertThat(service.searchManagedBooks(new BookSearchQuery("Algorithms", null, false, 1, 20)).items())
                .isEmpty();
        assertThat(service.getBook("book-1").copies()).hasSize(1);
    }

    @Test
    void rejectsBlankFirstCopyLocationBeforeCreatingCatalogEntry() {
        assertThatThrownBy(() -> service.createBook(new CreateBookCommand("9787300000002", "Algorithms",
                "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER", "Intro", " ", "BC-NEW")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.searchManagedBooks(new BookSearchQuery("Algorithms", null, false, 1, 20)).items())
                .isEmpty();
    }

    @Test
    void duplicateFirstCopyBarcodeRollsBackCatalogEntry() {
        assertThatThrownBy(() -> service.createBook(new CreateBookCommand("9787300000002", "Algorithms",
                "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER", "Intro", "A-02", "BC-1")))
                .isInstanceOf(DuplicateBarcodeException.class);
        assertThat(service.searchManagedBooks(new BookSearchQuery("Algorithms", null, false, 1, 20)).items()).isEmpty();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "123456789012345678901234567890123"})
    void rejectsInvalidFirstCopyBarcode(String barcode) {
        assertThatThrownBy(() -> service.createBook(new CreateBookCommand("9787300000002", "Algorithms",
                "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER", "Intro", "A-02", barcode)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.searchManagedBooks(new BookSearchQuery("Algorithms", null, false, 1, 20)).items()).isEmpty();
    }

    @Test
    void searchesDetailsAndTracksCurrentAndHistoricalLoans() {
        assertThat(service.searchBooks(new BookSearchQuery("Java", null, true, 1, 20)).items())
                .singleElement().extracting(summary -> summary.availableCopies()).isEqualTo(1);
        assertThat(service.getBook("book-1").copies()).hasSize(1);

        var borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));
        assertThat(service.getCurrentLoans("token")).singleElement().satisfies(current -> {
            assertThat(current.loanId()).isEqualTo(borrowed.loanId());
            assertThat(current.bookTitle()).isEqualTo("Java 21");
            assertThat(current.copyBarcode()).isEqualTo("BC-1");
        });
        service.returnBook("token", new ReturnBookCommand(borrowed.loanId(), 0));

        assertThat(service.getCurrentLoans("token")).isEmpty();
        assertThat(service.getLoanHistory("token", new LoanHistoryQuery(null, 1, 20)).items())
                .singleElement().extracting(view -> view.status()).isEqualTo(LoanStatus.RETURNED);
    }

    @Test
    void performsCatalogCopyAndPolicyAdministration() {
        var created = service.createBook(new CreateBookCommand("9787300000002", "Algorithms",
                "Author", "SEU Press", LocalDate.of(2026, 8, 24), "COMPUTER", "Intro"));
        var updated = service.updateBook(new UpdateBookCommand(created.bookId(), created.isbn(),
                "Algorithms 2", created.author(), created.publisher(), created.publishDate(),
                created.category(), created.description(), true, created.rowVersion()));
        var copy = service.addCopy(new AddBookCopyCommand(created.bookId(), "BC-NEW", "LIB-B-01"));
        var changed = service.changeCopyStatus(new ChangeCopyStatusCommand(copy.copyId(),
                CopyStatus.DAMAGED, copy.rowVersion()));
        var policy = service.updatePolicy(new UpdateLibraryPolicyCommand(
                "STUDENT", 6, 31, 2, 16, 0));

        assertThat(updated.title()).isEqualTo("Algorithms 2");
        assertThat(updated.rowVersion()).isEqualTo(1);
        assertThat(changed.status()).isEqualTo(CopyStatus.DAMAGED);
        assertThat(changed.rowVersion()).isEqualTo(1);
        assertThat(policy.maxActiveLoans()).isEqualTo(6);
        assertThat(policy.rowVersion()).isEqualTo(1);
    }

    @Test
    void loadsCurrentPolicyValuesAndVersionsBeforeEditing() {
        assertThat(service.getPolicies())
                .extracting(policy -> policy.roleCode(), policy -> policy.rowVersion())
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("STUDENT", 0L),
                        org.assertj.core.groups.Tuple.tuple("TEACHER", 0L));
    }

    @Test
    void activeLoanCannotBeBypassedByChangingOnlyTheCopyStatus() {
        service.borrow("token", new BorrowBookCommand("copy-1"));

        assertThatThrownBy(() -> service.changeCopyStatus(
                new ChangeCopyStatusCommand("copy-1", CopyStatus.AVAILABLE, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active loan");
    }

    @Test
    void administratorReturnSynchronizesLoanAndCopy() {
        var borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));

        service.resolveLoan(new AdminResolveLoanCommand(
                borrowed.loanId(), LoanStatus.RETURNED, borrowed.rowVersion()));

        assertThat(service.getCurrentLoans("token")).isEmpty();
        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.status()).isEqualTo(CopyStatus.AVAILABLE);
    }

    @Test
    void borrowedAndLostStatesCannotBeAssignedWithoutResolvingALoan() {
        assertThatThrownBy(() -> service.changeCopyStatus(
                new ChangeCopyStatusCommand("copy-1", CopyStatus.BORROWED, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changeCopyStatus(
                new ChangeCopyStatusCommand("copy-1", CopyStatus.LOST, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inactiveTitleRejectsNewCopiesAndBorrowing() {
        var book = service.getBook("book-1");
        service.updateBook(new UpdateBookCommand(book.bookId(), book.isbn(), book.title(),
                book.author(), book.publisher(), book.publishDate(), book.category(),
                book.description(), false, book.rowVersion()));

        assertThatThrownBy(() -> service.addCopy(
                new AddBookCopyCommand("book-1", "BC-INACTIVE", "LIB-Z-01")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.borrow("token", new BorrowBookCommand("copy-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inactiveTitleStopsRenewalsButStillAllowsReturns() {
        var borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));
        var book = service.getBook("book-1");
        service.updateBook(new UpdateBookCommand(book.bookId(), book.isbn(), book.title(),
                book.author(), book.publisher(), book.publishDate(), book.category(),
                book.description(), false, book.rowVersion()));

        assertThatThrownBy(() -> service.renew("token",
                new RenewLoanCommand(borrowed.loanId(), borrowed.rowVersion())))
                .isInstanceOf(InactiveBookException.class);
        assertThat(service.returnBook("token",
                new ReturnBookCommand(borrowed.loanId(), borrowed.rowVersion())).status())
                .isEqualTo(LoanStatus.RETURNED);
    }

    @Test
    void inactiveTitleRemainsVisibleToAdministratorsForReactivation() {
        var book = service.getBook("book-1");
        service.updateBook(new UpdateBookCommand(book.bookId(), book.isbn(), book.title(),
                book.author(), book.publisher(), book.publishDate(), book.category(),
                book.description(), false, book.rowVersion()));

        assertThat(service.searchBooks(new BookSearchQuery("Java", null, false, 1, 20)).items())
                .isEmpty();
        assertThat(service.searchManagedBooks(
                new BookSearchQuery("Java", null, false, 1, 20)).items())
                .singleElement().satisfies(summary -> {
                    assertThat(summary.bookId()).isEqualTo("book-1");
                    assertThat(summary.active()).isFalse();
                });
    }

    @Test
    void foundLostCopyReturnsToShelvesWithoutRewritingLostHistory() {
        var borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));
        service.resolveLoan(new AdminResolveLoanCommand(
                borrowed.loanId(), LoanStatus.LOST, borrowed.rowVersion()));

        service.changeCopyStatus(new ChangeCopyStatusCommand("copy-1", CopyStatus.AVAILABLE, 2));

        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.status()).isEqualTo(CopyStatus.AVAILABLE);
        assertThat(service.getLoanHistory("token", new LoanHistoryQuery(null, 1, 20)).items())
                .singleElement().extracting(loan -> loan.status()).isEqualTo(LoanStatus.LOST);
    }

    @Test
    void noOpCopyStateChangeDoesNotConsumeAnotherAdministratorVersion() {
        assertThatThrownBy(() -> service.changeCopyStatus(
                new ChangeCopyStatusCommand("copy-1", CopyStatus.AVAILABLE, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.getBook("book-1").copies()).singleElement()
                .extracting(copy -> copy.rowVersion()).isEqualTo(0L);
    }

    @Test
    void staleSameTargetCopyChangeIsReportedAsAConcurrencyConflict() {
        service.changeCopyStatus(new ChangeCopyStatusCommand(
                "copy-1", CopyStatus.DAMAGED, 0));

        assertThatThrownBy(() -> service.changeCopyStatus(
                new ChangeCopyStatusCommand("copy-1", CopyStatus.DAMAGED, 0)))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
    }

    @Test
    void duplicateCatalogIdentifiersHaveSpecificFailures() {
        var second = service.createBook(new CreateBookCommand("9787300000002", "第二书目",
                "作者", "出版社", LocalDate.of(2026, 1, 1), "计算机", ""));
        assertThatThrownBy(() -> service.createBook(new CreateBookCommand("9787300000001", "重复书目",
                "作者", "出版社", LocalDate.of(2026, 1, 1), "计算机", "")))
                .isInstanceOf(DuplicateIsbnException.class);
        assertThatThrownBy(() -> service.updateBook(new UpdateBookCommand(second.bookId(),
                "9787300000001", second.title(), second.author(), second.publisher(),
                second.publishDate(), second.category(), second.description(), true,
                second.rowVersion()))).isInstanceOf(DuplicateIsbnException.class);
        assertThatThrownBy(() -> service.addCopy(new AddBookCopyCommand("book-1", "BC-1", "A-02")))
                .isInstanceOf(DuplicateBarcodeException.class);
    }

    @Test
    void policyVersionPreventsOneAdministratorFromOverwritingAnother() {
        service.updatePolicy(new UpdateLibraryPolicyCommand("STUDENT", 6, 31, 2, 16, 0));

        assertThatThrownBy(() -> service.updatePolicy(
                new UpdateLibraryPolicyCommand("STUDENT", 8, 40, 3, 20, 0)))
                .isInstanceOf(java.util.ConcurrentModificationException.class);
        assertThat(service.getPolicies()).filteredOn(policy -> "STUDENT".equals(policy.roleCode()))
                .singleElement().extracting(policy -> policy.maxActiveLoans()).isEqualTo(6);
    }

    @Test
    void invalidPolicyCannotMakeCirculationRulesUnusable() {
        assertThatThrownBy(() -> service.updatePolicy(
                new UpdateLibraryPolicyCommand("STUDENT", 0, 30, 1, 15, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updatePolicy(
                new UpdateLibraryPolicyCommand("STUDENT", 5, 0, 1, 15, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.updatePolicy(
                new UpdateLibraryPolicyCommand("ADMIN", 5, 30, 1, 15, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
