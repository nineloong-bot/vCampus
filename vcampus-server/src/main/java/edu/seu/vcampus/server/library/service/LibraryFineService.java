package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.LibraryFineQuery;
import edu.seu.vcampus.common.library.LibraryFineView;
import edu.seu.vcampus.common.library.LoanStatus;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.wallet.WalletOperationResult;
import edu.seu.vcampus.server.concurrency.ResourceKey;
import edu.seu.vcampus.server.concurrency.ResourceLockManager;
import edu.seu.vcampus.server.library.repository.AccessLibraryFineRepository;
import edu.seu.vcampus.server.library.repository.LoanRepository;
import edu.seu.vcampus.server.persistence.TransactionContext;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.wallet.service.WalletFinePort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pays immutable assessed fines through the shared wallet, always scoped to the borrower. */
public final class LibraryFineService {
    private final LibraryIdentityPort identities;
    private final LoanRepository loans;
    private final AccessLibraryFineRepository fines;
    private final TransactionManager transactions;
    private final ResourceLockManager locks;
    private final WalletFinePort wallet;

    /** Connects library records and the wallet gateway using shared transactions and resource locks. */
    public LibraryFineService(LibraryIdentityPort identities, LoanRepository loans,
            AccessLibraryFineRepository fines, TransactionManager transactions,
            ResourceLockManager locks, WalletFinePort wallet) {
        this.identities = Objects.requireNonNull(identities);
        this.loans = Objects.requireNonNull(loans);
        this.fines = Objects.requireNonNull(fines);
        this.transactions = Objects.requireNonNull(transactions);
        this.locks = Objects.requireNonNull(locks);
        this.wallet = Objects.requireNonNull(wallet);
    }

    /** Returns only the signed-in borrower's assessed fines and durable payment state. */
    public PageResult<LibraryFineView> mine(String token, LibraryFineQuery query) {
        return find(identities.requireBorrower(token).userId(), query);
    }

    /** Returns all assessed fines; the transport must first require LIBRARY_ADMIN. */
    public PageResult<LibraryFineView> all(LibraryFineQuery query) {
        return find(null, query);
    }

    /** Pays a finalized fine, with amount and ownership rechecked inside the locked transaction. */
    public WalletOperationResult pay(String token, String loanId) {
        if (loanId == null || loanId.isBlank() || loanId.length() > 36)
            throw new IllegalArgumentException("Invalid loan identifier");
        var borrower = identities.requireBorrower(token);
        return locks.withLocks(List.of(new ResourceKey("LOAN", loanId),
                new ResourceKey("WALLET", borrower.userId())), () -> transactions.inTransaction(connection -> {
            var loan = loans.require(connection, loanId);
            if (!loan.borrowerUserId().equals(borrower.userId())) throw new LoanOwnershipException(loanId);
            if (loan.status() != LoanStatus.RETURNED && loan.status() != LoanStatus.LOST)
                throw new IllegalArgumentException("Fine is not finalized");
            long cents = cents(loan.overdueFine().add(loan.damageFine()));
            return wallet.pay(new TransactionContext(connection), loanId, borrower.userId(), cents);
        }));
    }

    private PageResult<LibraryFineView> find(String userId, LibraryFineQuery query) {
        if (query == null || query.page() < 1 || query.pageSize() < 1 || query.pageSize() > 100)
            throw new IllegalArgumentException("Invalid fine page");
        return transactions.inTransaction(connection -> {
            var page = fines.find(connection, userId, query);
            var items = new ArrayList<LibraryFineView>();
            for (var loan : page.items()) {
                var receipt = wallet.receipt(new TransactionContext(connection), loan.loanId(),
                        loan.borrowerUserId(), cents(loan.totalFine()));
                items.add(new LibraryFineView(loan, receipt == null ? null : receipt.operationId()));
            }
            return new PageResult<>(items, page.page(), page.pageSize(), page.total());
        });
    }

    private static long cents(BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("No payable fine");
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Invalid fine amount");
        }
    }
}
