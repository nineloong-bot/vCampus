package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SellerOrderServiceTest {
    private ShopTestDatabase database;

    @AfterEach void tearDown() throws Exception { if (database != null) database.close(); }

    @Test
    void approvedSellerCanReadOwnedEmptyOrderHistory() throws Exception {
        database = new ShopTestDatabase();
        var repository = new AccessShopRepository();
        var users = new FakeShopUserPort();
        users.add("owner-token", "owner-1", ShopUserKind.TEACHER, true);
        users.add("admin-token", "admin-1", ShopUserKind.ADMINISTRATOR, true);
        var transactions = new TransactionManager(database.connections());
        var locks = new StripedResourceLockManager();
        var clock = Clock.fixed(Instant.parse("2026-08-28T07:00:00Z"), ZoneOffset.UTC);
        var applications = new SellerApplicationService(repository, users, transactions, locks, clock);
        var admin = new ShopAdminService(repository, users, transactions, locks, clock);
        var draft = applications.saveDraft("owner-token", new SaveSellerDraftCommand(null,
                "订单店铺", "简介", "文具", "contact", "经营计划", 0));
        var pending = applications.submitApplication("owner-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        admin.reviewApplication("admin-token", new ReviewSellerApplicationCommand(
                pending.applicationId(), SellerReviewDecision.APPROVE, null, pending.rowVersion()));
        SellerOrderService orders = new SellerOrderService(repository, users, transactions);

        assertThat(orders.getOwnedOrders("owner-token", new SellerOrderQuery(null, 0, 20))
                .orders()).isEmpty();
    }
}
