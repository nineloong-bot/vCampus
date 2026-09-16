package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.library.domain.*;
import edu.seu.vcampus.server.library.repository.*;
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
    private final ReservationRepository reservations;
    private final ReservationQueueService queue;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final Clock clock;
    private final Supplier<String> idGenerator;
    private final LibraryReadAdminOperations operations;

    public LibraryServiceImpl(LibraryIdentityPort identities, BookRepository books,
            LoanRepository loans, LibraryPolicyRepository policies,
            ReservationRepository reservations, TransactionManager transactions,
            ResourceLockManager locks, Clock clock, Supplier<String> idGenerator) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.books = Objects.requireNonNull(books, "books");
        this.loans = Objects.requireNonNull(loans, "loans");
        this.policies = Objects.requireNonNull(policies, "policies");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.queue = new ReservationQueueService(books, reservations, policies);
        this.operations = new LibraryReadAdminOperations(identities, books, loans, policies,
                reservations, queue, transactions, clock, idGenerator);
    }

    @Override
    public PageResult<BookSummary> searchBooks(BookSearchQuery query) {
        return operations.searchBooks(query);
    }

    @Override
    public PageResult<BookSummary> searchManagedBooks(BookSearchQuery query) {
        return operations.searchManagedBooks(query);
    }

    @Override
    public BookDetail getBook(String bookId) {
        return operations.getBook(bookId);
    }

    @Override
    public LoanView borrow(String sessionToken, BorrowBookCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        BookCopy snapshot = transactions.inTransaction(connection ->
                books.requireCopy(connection, command.copyId()));
        List<ResourceKey> keys = List.of(
                new ResourceKey("LIBRARY_USER", borrower.userId()),
                new ResourceKey("BOOK", snapshot.bookId()),
                new ResourceKey("BOOK_COPY", command.copyId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection -> {
            Instant now = clock.instant();
            queue.refreshExpired(connection, now);
            if (loans.hasOverdueLoan(connection, borrower.userId(), now)) {
                throw new UserHasOverdueLoansException(borrower.userId());
            }
            LoanPolicy policy = policies.require(connection, borrower.roleCode());
            if (loans.countEffectiveLoans(connection, borrower.userId(), now)
                    >= policy.maxActiveLoans()) {
                throw new LoanLimitReachedException(borrower.userId());
            }
            BookCopy copy = books.requireCopy(connection, command.copyId());
            Book book = books.requireBook(connection, copy.bookId());
            if (!book.active()) throw new InactiveBookException(book.bookId());
            BookReservation hold = null;
            if (copy.status() == CopyStatus.RESERVED) {
                hold = queue.readyHolder(connection, copy.copyId());
                if (hold == null || !hold.userId().equals(borrower.userId())) {
                    throw new CopyUnavailableException(copy.copyId());
                }
            } else if (copy.status() != CopyStatus.AVAILABLE) {
                throw new CopyUnavailableException(copy.copyId());
            }
            Loan loan = new Loan(idGenerator.get(), copy.copyId(), borrower.userId(), now,
                    now.plus(policy.loanDays(), ChronoUnit.DAYS), null, 0,
                    LoanStatus.ACTIVE, 0, borrower.roleCode(), java.math.BigDecimal.ZERO,
                    java.math.BigDecimal.ZERO, ReturnCondition.NORMAL);
            loans.insert(connection, loan);
            books.updateCopyStatus(connection, copy.copyId(), CopyStatus.BORROWED,
                    copy.rowVersion());
            if (hold != null) {
                BookReservation fulfilled = new BookReservation(hold.reservationId(), hold.copyId(),
                        hold.bookId(), hold.userId(), hold.reserverRoleCode(), hold.reservedAt(),
                        hold.queueOrder(), ReservationStatus.FULFILLED, hold.readyAt(),
                        hold.expiresAt(), hold.rowVersion() + 1);
                reservations.update(connection, fulfilled, hold.rowVersion());
            }
            return toView(loan, copy.bookId());
        }));
    }
    @Override
    public BookReservationView reserve(String sessionToken, ReserveBookCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        BookCopy snapshot = transactions.inTransaction(connection ->
                books.requireCopy(connection, command.copyId()));
        List<ResourceKey> keys = List.of(
                new ResourceKey("LIBRARY_USER", borrower.userId()),
                new ResourceKey("BOOK", snapshot.bookId()),
                new ResourceKey("BOOK_COPY", command.copyId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection -> {
            Instant now = clock.instant();
            queue.refreshExpired(connection, now);
            BookCopy copy = books.requireCopy(connection, command.copyId());
            Book book = books.requireBook(connection, copy.bookId());
            if (!book.active()) throw new InactiveBookException(book.bookId());
            if (copy.status() != CopyStatus.BORROWED && copy.status() != CopyStatus.RESERVED) {
                throw new ReservationNotAllowedException(copy.copyId());
            }
            if (reservations.hasOpenForUserAndCopy(connection, copy.copyId(), borrower.userId())) {
                throw new DuplicateReservationException(borrower.userId(), copy.copyId());
            }
            BookReservation created = new BookReservation(idGenerator.get(), copy.copyId(),
                    copy.bookId(), borrower.userId(), borrower.roleCode(), now,
                    reservations.nextQueueOrder(connection, copy.copyId()), ReservationStatus.WAITING,
                    null, null, 0);
            reservations.insert(connection, created);
            queue.promoteNextReader(connection, copy.copyId(), now);
            return toReservationView(connection, created);
        }));
    }

    @Override
    public BookReservationView cancelReservation(String sessionToken, CancelReservationCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        BookReservation snapshot = transactions.inTransaction(connection ->
                reservations.require(connection, command.reservationId()));
        return locks.withLocks(List.of(
                new ResourceKey("BOOK_COPY", snapshot.copyId()),
                new ResourceKey("RESERVATION", command.reservationId())),
                () -> transactions.inTransaction(connection -> {
                    BookReservation reservation = reservations.require(connection, command.reservationId());
                    if (!reservation.userId().equals(borrower.userId())) {
                        throw new ReservationOwnershipException(command.reservationId());
                    }
                    return cancelInternal(connection, reservation, command.expectedVersion(), clock.instant());
                }));
    }

    @Override
    public List<BookReservationView> getMyReservations(String sessionToken) {
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        return transactions.inTransaction(connection -> {
            queue.refreshExpired(connection, clock.instant());
            return reservations.findForUser(connection, borrower.userId(), clock.instant());
        });
    }

    @Override
    public PageResult<BookReservationView> searchReservations(AdminReservationSearchQuery query) {
        return operations.searchReservations(query);
    }

    @Override
    public BookReservationView adminCancelReservation(AdminCancelReservationCommand command) {
        Objects.requireNonNull(command, "command");
        BookReservation snapshot = transactions.inTransaction(connection ->
                reservations.require(connection, command.reservationId()));
        return locks.withLocks(List.of(
                new ResourceKey("BOOK_COPY", snapshot.copyId()),
                new ResourceKey("RESERVATION", command.reservationId())),
                () -> transactions.inTransaction(connection -> {
                    BookReservation reservation = reservations.require(connection, command.reservationId());
                    return cancelInternal(connection, reservation, command.expectedVersion(), clock.instant());
                }));
    }

    private BookReservationView cancelInternal(java.sql.Connection connection,
            BookReservation reservation, long expectedVersion, Instant now)
            throws java.sql.SQLException {
        if (reservation.status() != ReservationStatus.WAITING
                && reservation.status() != ReservationStatus.READY) {
            throw new ReservationNotActiveException(reservation.reservationId());
        }
        BookReservation cancelled = new BookReservation(reservation.reservationId(), reservation.copyId(),
                reservation.bookId(), reservation.userId(), reservation.reserverRoleCode(),
                reservation.reservedAt(), reservation.queueOrder(), ReservationStatus.CANCELLED,
                reservation.readyAt(), reservation.expiresAt(), reservation.rowVersion() + 1);
        reservations.update(connection, cancelled, expectedVersion);
        queue.promoteNextReader(connection, reservation.copyId(), now);
        return toReservationView(connection, cancelled);
    }

    private BookReservationView toReservationView(java.sql.Connection connection,
            BookReservation reservation) throws java.sql.SQLException {
        int position = 0;
        if (reservation.status() == ReservationStatus.WAITING
                || reservation.status() == ReservationStatus.READY) {
            position = reservations.queuePosition(connection, reservation.copyId(),
                    reservation.queueOrder());
        }
        return new BookReservationView(reservation.reservationId(), reservation.copyId(),
                reservation.bookId(), reservation.userId(), null, null, null, reservation.status(),
                reservation.reservedAt(), reservation.readyAt(), reservation.expiresAt(), position,
                reservation.rowVersion());
    }
    @Override
    public LoanView returnBook(String sessionToken, ReturnBookCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.condition() == null || command.condition() == ReturnCondition.LOST)
            throw new IllegalArgumentException("Return condition must be normal or damaged");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        Loan snapshot = transactions.inTransaction(connection ->
                loans.require(connection, command.loanId()));
        return locks.withLocks(List.of(
                new ResourceKey("LOAN", command.loanId()),
                new ResourceKey("BOOK_COPY", snapshot.copyId())),
                () -> transactions.inTransaction(connection -> {
                Loan loan = loans.require(connection, command.loanId());
                requireOwnership(loan, borrower);
                if (loan.status() == LoanStatus.RETURNED || loan.returnedAt() != null) {
                    throw new LoanAlreadyReturnedException(loan.loanId());
                }
                if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.OVERDUE) {
                    throw new LoanNotActiveException(loan.loanId());
                }
                BookCopy copy = books.requireCopy(connection, loan.copyId());
                Instant now = clock.instant();
                PenaltyPolicy penalty = policies.require(connection, loan.borrowerRoleCode()).penalties();
                Loan returned = new Loan(loan.loanId(), loan.copyId(), loan.borrowerUserId(),
                        loan.borrowedAt(), loan.dueAt(), now, loan.renewCount(),
                        LoanStatus.RETURNED, loan.rowVersion() + 1, loan.borrowerRoleCode(),
                        penalty.overdueFine(loan.dueAt(), now), penalty.damageFine(command.condition()), command.condition());
                loans.update(connection, returned, command.expectedVersion());
                CopyStatus nextStatus = command.condition() == ReturnCondition.NORMAL
                        ? CopyStatus.AVAILABLE : CopyStatus.DAMAGED;
                books.updateCopyStatus(connection, copy.copyId(), nextStatus, copy.rowVersion());
                if (nextStatus == CopyStatus.AVAILABLE) {
                    queue.promoteNextReader(connection, copy.copyId(), now);
                }
                return toView(returned, copy.bookId());
            }));
    }

    @Override
    public LoanView renew(String sessionToken, RenewLoanCommand command) {
        Objects.requireNonNull(command, "command");
        BorrowerIdentity borrower = identities.requireBorrower(sessionToken);
        Loan snapshot = transactions.inTransaction(connection ->
                loans.require(connection, command.loanId()));
        BookCopy copySnapshot = transactions.inTransaction(connection ->
                books.requireCopy(connection, snapshot.copyId()));
        List<ResourceKey> keys = List.of(
                new ResourceKey("LIBRARY_USER", borrower.userId()),
                new ResourceKey("LOAN", command.loanId()),
                new ResourceKey("BOOK", copySnapshot.bookId()));
        return locks.withLocks(keys, () -> transactions.inTransaction(connection -> {
            Loan loan = loans.require(connection, command.loanId());
            requireOwnership(loan, borrower);
            BookCopy copy = books.requireCopy(connection, loan.copyId());
            Book book = books.requireBook(connection, copy.bookId());
            if (!book.active()) throw new InactiveBookException(book.bookId());
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
                    null, loan.renewCount() + 1, LoanStatus.ACTIVE, loan.rowVersion() + 1,
                    loan.borrowerRoleCode(), loan.overdueFine(), loan.damageFine(), loan.returnCondition());
            loans.update(connection, renewed, command.expectedVersion());
            return toView(renewed, copy.bookId());
        }));
    }

    @Override
    public List<LoanView> getCurrentLoans(String sessionToken) {
        return operations.getCurrentLoans(sessionToken);
    }

    @Override
    public PageResult<LoanView> getLoanHistory(String sessionToken, LoanHistoryQuery query) {
        return operations.getLoanHistory(sessionToken, query);
    }

    @Override
    public BookView createBook(CreateBookCommand command) {
        return operations.createBook(command);
    }

    @Override
    public BookView updateBook(UpdateBookCommand command) {
        Objects.requireNonNull(command, "command");
        return locks.withLocks(List.of(new ResourceKey("BOOK", command.bookId())),
                () -> operations.updateBook(command));
    }

    @Override
    public BookCopyView addCopy(AddBookCopyCommand command) {
        Objects.requireNonNull(command, "command");
        return locks.withLocks(List.of(new ResourceKey("BOOK", command.bookId())),
                () -> operations.addCopy(command));
    }

    @Override
    public BookCopyView changeCopyStatus(ChangeCopyStatusCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.status() == CopyStatus.BORROWED || command.status() == CopyStatus.LOST
                || command.status() == CopyStatus.RESERVED) {
            throw new IllegalArgumentException(
                    "BORROWED, RESERVED and LOST must be assigned through loan or reservation operations");
        }
        return locks.withLocks(List.of(new ResourceKey("BOOK_COPY", command.copyId())),
                () -> transactions.inTransaction(connection -> {
                    BookCopy copy = books.requireCopy(connection, command.copyId());
                    if (loans.hasEffectiveLoanForCopy(connection, copy.copyId())) {
                        throw new CopyHasActiveLoanException(copy.copyId());
                    }
                    if (copy.rowVersion() != command.expectedVersion()) {
                        throw new java.util.ConcurrentModificationException(
                                "Book copy changed: " + copy.copyId());
                    }
                    if (copy.status() == command.status()) {
                        throw new IllegalArgumentException("Copy already has requested status");
                    }
                    books.updateCopyStatus(connection, copy.copyId(), command.status(), command.expectedVersion());
                    if (command.status() == CopyStatus.AVAILABLE) {
                        queue.promoteNextReader(connection, copy.copyId(), clock.instant());
                    }
                    BookCopy updated = books.requireCopy(connection, copy.copyId());
                    return new BookCopyView(updated.copyId(), updated.bookId(), updated.barcode(),
                            updated.locationCode(), updated.status(), updated.rowVersion());
                }));
    }

    @Override
    public LoanView resolveLoan(AdminResolveLoanCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.resolution() != LoanStatus.RETURNED && command.resolution() != LoanStatus.LOST) {
            throw new IllegalArgumentException("Resolution must be RETURNED or LOST");
        }
        if (command.condition() == null || (command.resolution() == LoanStatus.LOST)
                != (command.condition() == ReturnCondition.LOST)) {
            throw new IllegalArgumentException("Return condition must match the loan resolution");
        }
        Loan snapshot = transactions.inTransaction(connection -> loans.require(connection, command.loanId()));
        return locks.withLocks(List.of(new ResourceKey("LOAN", command.loanId()),
                new ResourceKey("BOOK_COPY", snapshot.copyId())), () -> transactions.inTransaction(connection -> {
            Loan loan = loans.require(connection, command.loanId());
            if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.OVERDUE) {
                throw new LoanNotActiveException(loan.loanId());
            }
            BookCopy copy = books.requireCopy(connection, loan.copyId());
            Instant now = clock.instant();
            PenaltyPolicy penalty = policies.require(connection, loan.borrowerRoleCode()).penalties();
            Instant returnedAt = command.resolution() == LoanStatus.RETURNED ? now : null;
            Loan resolved = new Loan(loan.loanId(), loan.copyId(), loan.borrowerUserId(), loan.borrowedAt(),
                    loan.dueAt(), returnedAt, loan.renewCount(), command.resolution(), loan.rowVersion() + 1,
                    loan.borrowerRoleCode(), penalty.overdueFine(loan.dueAt(), now),
                    penalty.damageFine(command.condition()), command.condition());
            loans.update(connection, resolved, command.expectedVersion());
            CopyStatus copyStatus = switch (command.condition()) {
                case NORMAL -> CopyStatus.AVAILABLE;
                case MINOR_DAMAGE, MAJOR_DAMAGE -> CopyStatus.DAMAGED;
                case LOST -> CopyStatus.LOST;
            };
            books.updateCopyStatus(connection, copy.copyId(), copyStatus, copy.rowVersion());
            if (copyStatus == CopyStatus.AVAILABLE) {
                queue.promoteNextReader(connection, copy.copyId(), now);
            }
            return toView(resolved, copy.bookId());
        }));
    }

    @Override
    public PageResult<LoanView> searchAllLoans(AdminLoanSearchQuery query) {
        return operations.searchAllLoans(query);
    }

    @Override
    public List<LibraryPolicyView> getPolicies() {
        return operations.getPolicies();
    }

    @Override
    public LibraryPolicyView updatePolicy(UpdateLibraryPolicyCommand command) {
        return operations.updatePolicy(command);
    }

    private static void requireOwnership(Loan loan, BorrowerIdentity borrower) {
        if (!loan.borrowerUserId().equals(borrower.userId())) {
            throw new LoanOwnershipException(loan.loanId());
        }
    }

    private static LoanView toView(Loan loan, String bookId) {
        return new LoanView(loan.loanId(), loan.copyId(), bookId, loan.borrowerUserId(),
                loan.borrowedAt(), loan.dueAt(), loan.returnedAt(), loan.renewCount(),
                loan.status(), loan.rowVersion(), null, null, null,
                loan.overdueFine(), loan.damageFine(), loan.returnCondition());
    }
}