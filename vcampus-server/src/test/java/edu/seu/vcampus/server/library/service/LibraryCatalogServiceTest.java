package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.AddBookCopyCommand;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.ChangeCopyStatusCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.CreateBookCommand;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.ReturnBookCommand;
import edu.seu.vcampus.common.library.UpdateBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

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
    void searchesDetailsAndTracksCurrentAndHistoricalLoans() {
        assertThat(service.searchBooks(new BookSearchQuery("Java", null, true, 1, 20)).items())
                .singleElement().extracting(summary -> summary.availableCopies()).isEqualTo(1);
        assertThat(service.getBook("book-1").copies()).hasSize(1);

        var borrowed = service.borrow("token", new BorrowBookCommand("copy-1"));
        assertThat(service.getCurrentLoans("token")).singleElement().isEqualTo(borrowed);
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
}
