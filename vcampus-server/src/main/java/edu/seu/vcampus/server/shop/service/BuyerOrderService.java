package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.repository.ShopRepository;

import java.util.Objects;

/** Loads paid order history scoped to an already authenticated buyer id. */
public final class BuyerOrderService {
    private final ShopRepository repository;
    private final TransactionManager transactions;

    /**
     * Creates a buyer order service with its required collaborators.
     * @param repository the repository
     * @param transactions the transactions
     */
    public BuyerOrderService(ShopRepository repository, TransactionManager transactions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /**
     * Performs the get paid orders operation.
     * @param buyerUserId the buyer user identifier
     * @return the operation result
     */
    public PaidOrderHistory getPaidOrders(String buyerUserId) {
        Objects.requireNonNull(buyerUserId, "buyerUserId");
        if (buyerUserId.isBlank()) {
            throw new IllegalArgumentException("buyerUserId is required");
        }
        return transactions.inTransaction(connection ->
                new PaidOrderHistory(repository.findPaidOrders(connection, buyerUserId)));
    }
}
