package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.library.domain.BookCopy;
import edu.seu.vcampus.server.library.domain.Loan;
import edu.seu.vcampus.server.library.domain.LoanPolicy;
import edu.seu.vcampus.server.library.repository.BookRepository;
import edu.seu.vcampus.server.library.repository.LibraryPolicyRepository;
import edu.seu.vcampus.server.library.repository.LoanRepository;
import edu.seu.vcampus.server.persistence.TransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Transactional, lock-protected implementation of library use cases. */
public final class LibraryServiceImpl implements LibraryService {
    private final LibraryIdentityPort identities;
    private final BookRepository books;
    private final LoanRepository loans;
    private final LibraryPolicyRepository policies;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    public LibraryServiceImpl(LibraryIdentityPort identities, BookRepository books,
            LoanRepository loans, LibraryPolicyRepository policies,
            TransactionManager transactions, ResourceLockManager locks, Clock clock,
            Supplier<String> idGenerator) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.books = Objects.requireNonNull(books, "books");
        this.loans = Objects.requireNonNull(loans, "loans");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public LoanView borrow(String sessionToken, BorrowBookCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        List<ResourceKey> keys = List.of(
                new ResourceKey("LIBRARY_USER", borrower.userId()),
                new ResourceKey("BOOK_COPY", command.copyId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection -> {
            Instant now = clock.instant();
            if (loans.hasOverdueLoan(connection, borrower.userId(), now)) {
                throw new UserHasOverdueLoansException(borrower.userId());
            }
            LoanPolicy policy = policies.require(connection, borrower.roleCode());
            if (loans.countEffectiveLoans(connection, borrower.userId(), now)
                    >= policy.maxActiveLoans()) {
                throw new LoanLimitReachedException(borrower.userId());
            }
            BookCopy copy = books.requireCopy(connection, command.copyId());
            if (copy.status() != CopyStatus.AVAILABLE) {
                throw new CopyUnavailableException(copy.copyId());
            }
            Loan loan = new Loan(idGenerator.get(), copy.copyId(), borrower.userId(), now,
                    now.plus(policy.loanDays(), ChronoUnit.DAYS), null, 0,
                    LoanStatus.ACTIVE, 0);
            loans.insert(connection, loan);
            books.updateCopyStatus(connection, copy.copyId(), CopyStatus.BORROWED,
                    copy.rowVersion());
            return toView(loan, copy.bookId());
        }));
    }

    private static LoanView toView(Loan loan, String bookId) {
        return new LoanView(loan.loanId(), loan.copyId(), bookId, loan.borrowerUserId(),
                loan.borrowedAt(), loan.dueAt(), loan.returnedAt(), loan.renewCount(),
                loan.status(), loan.rowVersion());
    }
}
