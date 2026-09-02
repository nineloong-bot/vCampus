package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.AddBookCopyCommand;
import edu.seu.vcampus.common.library.AdminLoanSearchQuery;
import edu.seu.vcampus.common.library.BookCopyView;
import edu.seu.vcampus.common.library.BookDetail;
import edu.seu.vcampus.common.library.BookSearchQuery;
import edu.seu.vcampus.common.library.BookSummary;
import edu.seu.vcampus.common.library.BookView;
import edu.seu.vcampus.common.library.ChangeCopyStatusCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.CreateBookCommand;
import edu.seu.vcampus.common.library.LibraryPolicyView;
import edu.seu.vcampus.common.library.LoanHistoryQuery;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.UpdateBookCommand;
import edu.seu.vcampus.common.library.UpdateLibraryPolicyCommand;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.library.domain.Book;
import edu.seu.vcampus.server.library.domain.BookCopy;
import edu.seu.vcampus.server.library.domain.LoanPolicy;
import edu.seu.vcampus.server.library.repository.BookRepository;
import edu.seu.vcampus.server.library.repository.LibraryPolicyRepository;
import edu.seu.vcampus.server.library.repository.LoanRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

final class LibraryReadAdminOperations {
    private final LibraryIdentityPort identities;
    private final BookRepository books;
    private final LoanRepository loans;
    private final LibraryPolicyRepository policies;
    private final TransactionManager transactions;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    LibraryReadAdminOperations(LibraryIdentityPort identities, BookRepository books,
            LoanRepository loans, LibraryPolicyRepository policies,
            TransactionManager transactions, Clock clock, Supplier<String> idGenerator) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.books = Objects.requireNonNull(books, "books");
        this.loans = Objects.requireNonNull(loans, "loans");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    PageResult<BookSummary> searchBooks(BookSearchQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> books.search(connection, query));
    }

    BookDetail getBook(String bookId) {
        return transactions.inTransaction(connection -> books.requireDetail(connection, bookId));
    }

    List<LoanView> getCurrentLoans(String sessionToken) {
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        return transactions.inTransaction(connection -> loans.findCurrentForUser(
                connection, borrower.userId(), clock.instant()));
    }

    PageResult<LoanView> getLoanHistory(String sessionToken, LoanHistoryQuery query) {
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        return transactions.inTransaction(connection -> loans.findHistoryForUser(
                connection, borrower.userId(), query, clock.instant()));
    }

    BookView createBook(CreateBookCommand command) {
        Objects.requireNonNull(command, "command");
        Book book = new Book(idGenerator.get(), command.isbn(), command.title(), command.author(),
                command.publisher(), command.publishDate(), command.category(), command.description(),
                true, 0);
        Book inserted = transactions.inTransaction(connection -> books.insertBook(connection, book));
        return toView(inserted);
    }

    BookView updateBook(UpdateBookCommand command) {
        Objects.requireNonNull(command, "command");
        Book book = new Book(command.bookId(), command.isbn(), command.title(), command.author(),
                command.publisher(), command.publishDate(), command.category(), command.description(),
                command.active(), command.expectedVersion() + 1);
        transactions.inTransaction(connection -> {
            books.updateBook(connection, book, command.expectedVersion());
            return null;
        });
        return toView(book);
    }

    BookCopyView addCopy(AddBookCopyCommand command) {
        Objects.requireNonNull(command, "command");
        BookCopy copy = new BookCopy(idGenerator.get(), command.bookId(), command.barcode(),
                command.locationCode(), CopyStatus.AVAILABLE, 0);
        BookCopy inserted = transactions.inTransaction(connection -> {
            Book book = books.requireBook(connection, command.bookId());
            if (!book.active()) throw new InactiveBookException(book.bookId());
            return books.insertCopy(connection, copy);
        });
        return toView(inserted);
    }

    PageResult<LoanView> searchAllLoans(AdminLoanSearchQuery query) {
        return transactions.inTransaction(connection ->
                loans.searchAll(connection, query, clock.instant()));
    }

    PageResult<BookSummary> searchManagedBooks(BookSearchQuery query) {
        Objects.requireNonNull(query, "query");
        return transactions.inTransaction(connection -> books.searchManaged(connection, query));
    }

    List<LibraryPolicyView> getPolicies() {
        return transactions.inTransaction(connection -> List.of(
                toView(policies.require(connection, "STUDENT")),
                toView(policies.require(connection, "TEACHER"))));
    }

    LibraryPolicyView updatePolicy(UpdateLibraryPolicyCommand command) {
        Objects.requireNonNull(command, "command");
        validatePolicy(command);
        LoanPolicy existing = transactions.inTransaction(connection ->
                policies.require(connection, command.roleCode()));
        LoanPolicy changed = new LoanPolicy(existing.policyId(), existing.roleCode(),
                command.maxActiveLoans(), command.loanDays(), command.maxRenewals(),
                command.renewalDays(), command.expectedVersion() + 1);
        transactions.inTransaction(connection -> policies.update(
                connection, changed, command.expectedVersion()));
        return toView(changed);
    }

    private static void validatePolicy(UpdateLibraryPolicyCommand command) {
        if (!"STUDENT".equals(command.roleCode()) && !"TEACHER".equals(command.roleCode())) {
            throw new IllegalArgumentException("Only STUDENT and TEACHER policies are configurable");
        }
        if (command.maxActiveLoans() < 1 || command.maxActiveLoans() > 100
                || command.loanDays() < 1 || command.loanDays() > 365
                || command.maxRenewals() < 0 || command.maxRenewals() > 20
                || command.renewalDays() < 1 || command.renewalDays() > 365
                || command.expectedVersion() < 0) {
            throw new IllegalArgumentException("Library policy values are outside supported limits");
        }
    }

    private static BookView toView(Book book) {
        return new BookView(book.bookId(), book.isbn(), book.title(), book.author(), book.publisher(),
                book.publishDate(), book.category(), book.description(), book.active(), book.rowVersion());
    }

    private static BookCopyView toView(BookCopy copy) {
        return new BookCopyView(copy.copyId(), copy.bookId(), copy.barcode(), copy.locationCode(),
                copy.status(), copy.rowVersion());
    }

    private static LibraryPolicyView toView(LoanPolicy policy) {
        return new LibraryPolicyView(policy.roleCode(), policy.maxActiveLoans(),
                policy.loanDays(), policy.maxRenewals(), policy.renewalDays(),
                policy.rowVersion());
    }
}
