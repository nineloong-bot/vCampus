package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerReviewDecision;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopOwnershipTest {
    private ShopTestDatabase database;
    private SellerApplicationService applications;
    private ShopAdminService admin;
    private SellerService sellers;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        var repository = new AccessShopRepository();
        var users = new FakeShopUserPort();
        users.add("owner-token", "owner-1", ShopUserKind.TEACHER, true);
        users.add("stranger-token", "stranger-1", ShopUserKind.STUDENT, true);
        users.add("admin-token", "admin-1", ShopUserKind.ADMINISTRATOR, true);
        var transactions = new TransactionManager(database.connections());
        var locks = new StripedResourceLockManager();
        var clock = Clock.fixed(Instant.parse("2026-08-28T06:30:00Z"), ZoneOffset.UTC);
        applications = new SellerApplicationService(repository, users, transactions, locks, clock);
        admin = new ShopAdminService(repository, users, transactions, locks, clock);
        sellers = new SellerService(repository, users, transactions);
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void onlyTheApprovedOwnerReceivesSellerCapability() {
        var draft = applications.saveDraft("owner-token", new SaveSellerDraftCommand(
                null, "教师书屋", "教材与文具", "图书", "owner@example.edu", "经营计划", 0));
        var pending = applications.submitApplication("owner-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        admin.reviewApplication("admin-token", new ReviewSellerApplicationCommand(pending.applicationId(),
                SellerReviewDecision.APPROVE, null, pending.rowVersion()));

        assertThat(sellers.requireOwnedActiveShop("owner-token").ownerUserId()).isEqualTo("owner-1");
        assertThatThrownBy(() -> sellers.requireOwnedActiveShop("stranger-token"))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_SELLER_NOT_APPROVED));
    }
}
