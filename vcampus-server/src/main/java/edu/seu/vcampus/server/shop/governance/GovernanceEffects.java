package edu.seu.vcampus.server.shop.governance;

import java.sql.Connection;
import java.sql.SQLException;

/** Transaction-preserving integration with orders and catalog. */
public interface GovernanceEffects {
    /** Cancels pending orders and removes shop items from carts in this transaction. */
    void shopSuspended(Connection connection,String shopId) throws SQLException;
    /** Removes affected products from carts; existing paid orders remain intact. */
    void productRestricted(Connection connection,String productId) throws SQLException;
    /** Checks catalog completeness, deletion and other independent restoration rules. */
    boolean mayRestore(Connection connection,String productId) throws SQLException;
}
