package edu.seu.vcampus.server.library.service;

import edu.seu.vcampus.common.library.*;
import edu.seu.vcampus.common.shop.order.*;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.library.repository.AccessLibraryFineRepository;
import edu.seu.vcampus.server.shop.order.OrderService;
import edu.seu.vcampus.server.wallet.service.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

final class FineCommerceFixture {
    final LibraryServiceFixture db = new LibraryServiceFixture();
    final WalletService wallet;
    final LibraryFineService fines;
    final OrderService orders;
    final String loan;

    FineCommerceFixture() throws Exception {
        db.seedCopies(1);
        db.addIdentity("student", "user-1", "STUDENT");
        try (var c = db.connections.open(); var s = c.createStatement()) {
            s.execute("INSERT INTO tblRole VALUES ('STUDENT','Student')");
            for (String id : List.of("user-1", "seller")) {
                s.execute("INSERT INTO tblUser (userId,loginId,passwordHash,passwordSalt,passwordIterations,roleCode,"
                        + "accountStatus,mustChangePassword,createdAt,updatedAt) VALUES ('" + id + "','" + id
                        + "','test','test',1,'STUDENT','ACTIVE',FALSE,#2026-08-01#,#2026-08-01#)");
            }
            for (String name : List.of("050_shop.sql", "051_shop_wallet.sql", "053_shop_orders.sql")) {
                String script = Files.readString(Path.of("../vcampus-database/schema/" + name));
                for (String sql : script.replace("\uFEFF", "").split(";")) {
                    if (!sql.isBlank()) s.execute(sql);
                }
            }
            s.execute("INSERT INTO tblShop (shopId,ownerUserId,shopName,normalizedShopName,description,category,"
                    + "contact,shopStatus,createdAt,updatedAt) VALUES ('shop','seller','Shop','shop','test',"
                    + "'test','test','ACTIVE',NOW(),NOW())");
            s.execute("INSERT INTO tblProduct (productId,shopId,productName,normalizedProductName,category,"
                    + "description,productStatus,createdAt,updatedAt) VALUES ('product','shop','Book','book',"
                    + "'test','test','ACTIVE',NOW(),NOW())");
            s.execute("INSERT INTO tblProductSku (skuId,productId,skuName,unitPrice,stockQuantity) "
                    + "VALUES ('sku','product','Standard',80.00,20)");
        }
        var overdue = db.seedOverdue("copy-1", "user-1");
        db.service().resolveLoan(new AdminResolveLoanCommand(overdue.loanId(), LoanStatus.RETURNED, 0));
        loan = overdue.loanId();
        try (var c = db.connections.open(); var s = c.prepareStatement(
                "UPDATE tblBookLoan SET overdueFine=50,damageFine=0 WHERE loanId=?")) {
            s.setString(1, loan); s.executeUpdate();
        }
        Clock clock = Clock.systemUTC();
        // Match production: library and recharge have separate lock managers but share transactions.
        wallet = new WalletService(db.transactions, new StripedResourceLockManager(), clock);
        fines = new LibraryFineService(db.identities::get, db.loans, new AccessLibraryFineRepository(),
                db.transactions, new StripedResourceLockManager(), new WalletFineService(clock));
        orders = new OrderService(db.transactions, new WalletPostingService(clock), clock, (c, p) -> true);
    }

    OrderAction checkout(String key) {
        var result = orders.checkout("user-1", key, new CheckoutRequest(
                List.of(new OrderLine("sku", 1, new BigDecimal("80.00"))), false));
        return new OrderAction(List.of(result.orders().getFirst().orderId()), "测试退款");
    }

    boolean finePaid() {
        return fines.mine("student", new LibraryFineQuery(1, 20)).items().getFirst().paid();
    }

    String state(OrderAction order) {
        return orders.list("user-1", false, new OrderQuery("ALL", null)).orders().stream()
                .filter(o -> o.orderId().equals(order.orderIds().getFirst())).findFirst().orElseThrow().state();
    }

    long number(String sql) throws Exception {
        try (var c = db.connections.open(); var s = c.createStatement(); var r = s.executeQuery(sql)) {
            r.next(); return r.getLong(1);
        }
    }
}
