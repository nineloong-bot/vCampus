package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ResumeShopCommand;
import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerReviewDecision;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.ShopView;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SuspendShopCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.repository.ShopRepository;
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

class ShopStatusServiceTest {
    private ShopTestDatabase database;
    private SellerApplicationService sellerApplications;
    private ShopAdminService admin;
    private SellerService seller;

    @BeforeEach
    void setUp() throws Exception {
        database = new ShopTestDatabase();
        ShopRepository repository = new AccessShopRepository();
        var users = new FakeShopUserPort();
        users.add("owner-token", "owner-1", ShopUserKind.TEACHER, true);
        users.add("admin-token", "admin-7", ShopUserKind.ADMINISTRATOR, true);
        var transactions = new TransactionManager(database.connections());
        var locks = new StripedResourceLockManager();
        var clock = Clock.fixed(Instant.parse("2026-08-28T06:30:00Z"), ZoneOffset.UTC);
        sellerApplications = new SellerApplicationService(repository, users, transactions, locks, clock);
        admin = new ShopAdminService(repository, users, transactions, locks, clock);
        seller = new SellerService(repository, users, transactions);
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void suspensionRecordsAuditAndResumeKeepsApplicationApproved() {
        ShopView approved = approveOwnerShop();

        admin.suspendShop("admin-token", new SuspendShopCommand(approved.shopId(), "违规商品", approved.rowVersion()));
        ShopView suspended = seller.getOwnedShop("owner-token");

        assertThat(suspended.status()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo("违规商品");
        assertThat(suspended.suspendedByUserId()).isEqualTo("admin-7");
        assertThat(suspended.suspendedAt()).isEqualTo(Instant.parse("2026-08-28T06:30:00Z"));
        assertThat(sellerApplications.getMyApplication("owner-token").status())
                .isEqualTo(SellerApplicationStatus.APPROVED);

        admin.resumeShop("admin-token", new ResumeShopCommand(suspended.shopId(), suspended.rowVersion()));
        ShopView resumed = seller.requireOwnedActiveShop("owner-token");
        assertThat(resumed.status()).isEqualTo(ShopStatus.ACTIVE);
        assertThat(resumed.suspensionReason()).isNull();
        assertThat(resumed.suspendedByUserId()).isNull();
        assertThat(resumed.suspendedAt()).isNull();
    }

    @Test
    void suspensionRequiresReasonAndRejectsStaleVersion() {
        ShopView approved = approveOwnerShop();
        assertThatThrownBy(() -> admin.suspendShop("admin-token",
                new SuspendShopCommand(approved.shopId(), " ", approved.rowVersion())))
                .isInstanceOf(IllegalArgumentException.class);

        admin.suspendShop("admin-token", new SuspendShopCommand(approved.shopId(), "违规", approved.rowVersion()));
        assertThatThrownBy(() -> admin.resumeShop("admin-token",
                new ResumeShopCommand(approved.shopId(), approved.rowVersion())))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_CONCURRENT_MODIFICATION));
    }

    @Test
    void onlyActiveAdministratorSessionCanSearchAndMutateShops() {
        ShopView approved = approveOwnerShop();

        assertThat(admin.searchShops("admin-token", new ShopAdminQuery(null, null, 0, 20)).items())
                .extracting(summary -> summary.shopId()).containsExactly(approved.shopId());
        assertThatThrownBy(() -> admin.searchShops("owner-token",
                new ShopAdminQuery(null, null, 0, 20)))
                .isInstanceOfSatisfying(ShopAccessException.class,
                        error -> assertThat(error.code()).isEqualTo("AUTH_FORBIDDEN"));
    }

    private ShopView approveOwnerShop() {
        var draft = sellerApplications.saveDraft("owner-token", new SaveSellerDraftCommand(
                null, "教师书屋", "教材与文具", "图书", "owner@example.edu", "经营计划", 0));
        var pending = sellerApplications.submitApplication("owner-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        admin.reviewApplication("admin-token", new ReviewSellerApplicationCommand(pending.applicationId(),
                SellerReviewDecision.APPROVE, null, pending.rowVersion()));
        return seller.getOwnedShop("owner-token");
    }
}
