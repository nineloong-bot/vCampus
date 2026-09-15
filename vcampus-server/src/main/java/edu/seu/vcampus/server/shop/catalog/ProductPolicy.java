package edu.seu.vcampus.server.shop.catalog;

import java.sql.Connection;
import java.sql.SQLException;

/** Governance boundary evaluated inside catalog and order transactions. */
public interface ProductPolicy {
    /** Checks shop admission and current category qualification. */
    boolean mayPublish(Connection connection, String shopId, String category) throws SQLException;
    /** Checks independent emergency and qualification restrictions for a product. */
    boolean mayBuy(Connection connection, String productId) throws SQLException;
    /** Labels independent restrictions without rewriting the seller's intended lifecycle state. */
    default String effectiveStatus(Connection connection, String productId, String baseStatus) throws SQLException {
        return baseStatus;
    }
    /** Records a seller's deliberate withdrawal independently of automatic restrictions. */
    default void manualOff(Connection connection, String productId) throws SQLException { }
}
