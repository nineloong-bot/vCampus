import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.Row;
import io.github.spannm.jackcess.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Checks commerce references and independently reconciles the wallet ledger. */
final class CommerceIntegrity {
    private CommerceIntegrity() {
    }

    static void validate(Database database) throws Exception {
        Map<Object, Row> users = CommerceDataChecks.index(database.getTable("tblUser"), "userId");
        Set<String> userColumns = Set.of("userId", "ownerUserId", "applicantUserId", "reviewerUserId",
                "suspendedByUserId", "buyerUserId", "actorUserId", "actorId", "peerId", "buyerId", "sellerId");
        for (String name : MainCommerceMigration.OWNED) {
            Table table = database.getTable(name);
            if (table == null) {
                throw new IllegalStateException("Commerce schema missing " + name);
            }
            for (Row row : table) {
                for (String column : userColumns) {
                    Object value = row.get(column);
                    boolean sentinel = name.equals("tblWalletOperation") && column.equals("peerId")
                            && (("LIBRARY".equals(value) && "LIBRARY_FINE".equals(row.get("operationType")))
                            || ("SYSTEM".equals(value) && "RECHARGE".equals(row.get("operationType"))));
                    if (value != null && !sentinel && !users.containsKey(value)) {
                        throw new IllegalStateException("Missing account: " + name + "." + column + "=" + value);
                    }
                }
            }
        }
        String[] references = {
            "tblProduct shopId tblShop shopId", "tblProductSku productId tblProduct productId",
            "tblCartItem cartId tblCart cartId", "tblCartItem skuId tblProductSku skuId",
            "tblOrder orderGroupId tblOrderGroup orderGroupId", "tblOrder shopId tblShop shopId",
            "tblOrderItem orderId tblOrder orderId", "tblOrderItem skuId tblProductSku skuId",
            "tblPayment orderGroupId tblOrderGroup orderGroupId", "tblPaymentAttempt paymentId tblPayment paymentId",
            "tblInventoryReservation paymentId tblPayment paymentId",
            "tblInventoryReservation skuId tblProductSku skuId", "tblProductCatalog productId tblProduct productId",
            "tblProductCatalog defaultSkuId tblProductSku skuId", "tblSkuDraftFields skuId tblProductSku skuId",
            "tblShopOrderState orderId tblOrder orderId", "tblShopOrderLineState orderItemId tblOrderItem orderItemId",
            "tblShopOrderLineState productId tblProduct productId",
            "tblShopInventoryMovement orderItemId tblOrderItem orderItemId",
            "tblShopInventoryMovement skuId tblProductSku skuId", "tblShopOrderEvent orderId tblOrder orderId",
            "tblShopGovApplication applicationId tblSellerApplication applicationId",
            "tblShopQualification shopId tblShop shopId", "tblShopProductRestriction productId tblProduct productId",
            "tblShopGovCase shopId tblShop shopId", "tblWalletEntry operationId tblWalletOperation operationId",
            "tblWalletEscrow orderKey tblOrder orderId"
        };
        for (String reference : references) {
            String[] parts = reference.split(" ");
            Map<Object, Row> parents = CommerceDataChecks.index(database.getTable(parts[2]), parts[3]);
            for (Row row : database.getTable(parts[0])) {
                Object value = row.get(parts[1]);
                if (value != null && !parents.containsKey(value)) {
                    throw new IllegalStateException("Broken reference " + reference + ": " + value);
                }
            }
        }
        wallet(database, users);
    }

    private static void wallet(Database database, Map<Object, Row> users) throws Exception {
        Map<Object, Long> operations = new HashMap<>();
        Map<Object, Integer> entryCounts = new HashMap<>();
        Map<Object, Long> userBalances = new HashMap<>();
        Map<Object, Long> escrowBalances = new HashMap<>();
        for (Row row : database.getTable("tblWalletEntry")) {
            long delta = ((Number) row.get("deltaCents")).longValue();
            operations.merge(row.get("operationId"), delta, Math::addExact);
            entryCounts.merge(row.get("operationId"), 1, Integer::sum);
            String kind = row.get("accountKind").toString();
            Object key = row.get("accountKey");
            if (kind.equals("USER")) {
                require(users.containsKey(key), "Ledger user missing " + key);
                userBalances.merge(key, delta, Math::addExact);
            } else if (kind.equals("ESCROW")) {
                escrowBalances.merge(key, delta, Math::addExact);
            } else {
                require(kind.equals("SYSTEM"), "Unknown ledger account kind");
            }
        }
        for (Row row : database.getTable("tblWalletOperation")) {
            Object id = row.get("operationId");
            require(operations.containsKey(id) && operations.get(id) == 0L
                    && entryCounts.get(id) >= 2, "Unbalanced or missing operation ledger " + id);
        }
        for (Row row : database.getTable("tblWalletAccount")) {
            Object user = row.get("userId");
            long balance = ((Number) row.get("balanceCents")).longValue();
            require(balance >= 0 && balance == userBalances.getOrDefault(user, 0L),
                    "Wallet account balance differs from ledger " + user);
            userBalances.remove(user);
        }
        require(userBalances.values().stream().allMatch(v -> v == 0L), "Ledger account without wallet");
        for (Row row : database.getTable("tblWalletEscrow")) {
            Object order = row.get("orderKey");
            long expected = row.get("escrowStatus").equals("HELD")
                    ? ((Number) row.get("amountCents")).longValue() : 0L;
            require(expected == escrowBalances.getOrDefault(order, 0L), "Escrow differs from ledger " + order);
            escrowBalances.remove(order);
        }
        require(escrowBalances.values().stream().allMatch(v -> v == 0L), "Ledger escrow missing");
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalStateException(message);
        }
    }
}
