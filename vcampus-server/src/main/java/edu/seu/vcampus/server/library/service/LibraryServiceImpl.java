package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.BorrowBookCommand;
import edu.seu.vcampus.common.library.CopyStatus;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.library.LoanView;
import edu.seu.vcampus.common.library.RenewLoanCommand;
import edu.seu.vcampus.common.library.ReturnBookCommand;
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

    @Override
    public LoanView returnBook(String sessionToken, ReturnBookCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        ResourceKey loanKey = new ResourceKey("LOAN", command.loanId());
        return locks.withLocks(List.of(loanKey), () -> {
            Loan snapshot = transactions.inTransaction(connection ->
                    loans.require(connection, command.loanId()));
            ResourceKey copyKey = new ResourceKey("BOOK_COPY", snapshot.copyId());
            return locks.withLocks(List.of(copyKey), () -> transactions.inTransaction(connection -> {
                Loan loan = loans.require(connection, command.loanId());
                requireOwnership(loan, borrower);
                if (loan.status() == LoanStatus.RETURNED || loan.returnedAt() != null) {
                    throw new LoanAlreadyReturnedException(loan.loanId());
                }
                if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.OVERDUE) {
                    throw new LoanNotActiveException(loan.loanId());
                }
                BookCopy copy = books.requireCopy(connection, loan.copyId());
                Loan returned = new Loan(loan.loanId(), loan.copyId(), loan.borrowerUserId(),
                        loan.borrowedAt(), loan.dueAt(), clock.instant(), loan.renewCount(),
                        LoanStatus.RETURNED, loan.rowVersion() + 1);
                loans.update(connection, returned, command.expectedVersion());
                books.updateCopyStatus(connection, copy.copyId(), CopyStatus.AVAILABLE,
                        copy.rowVersion());
                return toView(returned, copy.bookId());
            }));
        });
    }

    @Override
    public LoanView renew(String sessionToken, RenewLoanCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        List<ResourceKey> keys = List.of(
                new ResourceKey("LIBRARY_USER", borrower.userId()),
                new ResourceKey("LOAN", command.loanId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection -> {
            Loan loan = loans.require(connection, command.loanId());
            requireOwnership(loan, borrower);
            Instant now = clock.instant();
            if (loan.status() == LoanStatus.OVERDUE || loan.dueAt().isBefore(now)) {
                throw new LoanOverdueException(loan.loanId());
            }
            if (loan.status() != LoanStatus.ACTIVE) {
                throw new LoanNotActiveException(loan.loanId());
            }
            LoanPolicy policy = policies.require(connection, borrower.roleCode());
            if (loan.renewCount() >= policy.maxRenewals()) {
                throw new RenewalLimitReachedException(loan.loanId());
            }
            Loan renewed = new Loan(loan.loanId(), loan.copyId(), loan.borrowerUserId(),
                    loan.borrowedAt(), loan.dueAt().plus(policy.renewalDays(), ChronoUnit.DAYS),
                    null, loan.renewCount() + 1, LoanStatus.ACTIVE, loan.rowVersion() + 1);
            loans.update(connection, renewed, command.expectedVersion());
            BookCopy copy = books.requireCopy(connection, loan.copyId());
            return toView(renewed, copy.bookId());
        }));
    }

    private static void requireOwnership(Loan loan, BorrowerIdentity borrower) {
        if (!loan.borrowerUserId().equals(borrower.userId())) {
            throw new LoanOwnershipException(loan.loanId());
        }
    }

    private static LoanView toView(Loan loan, String bookId) {
        return new LoanView(loan.loanId(), loan.copyId(), bookId, loan.borrowerUserId(),
                loan.borrowedAt(), loan.dueAt(), loan.returnedAt(), loan.renewCount(),
                loan.status(), loan.rowVersion());
    }
}
