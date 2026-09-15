package edu.seu.vcampus.server.shop.order;

import java.sql.Connection;
import java.sql.SQLException;

/** Same-connection catalog/governance rule, composed by the application root. */
@FunctionalInterface
public interface OrderAvailability {
    /** Checks additional product restrictions without opening a new transaction. */
    boolean purchasable(Connection connection, String productId) throws SQLException;
}
