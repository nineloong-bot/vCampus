package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ResumeShopCommand;
import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerReviewDecision;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopView;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SuspendShopCommand;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
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
        users.administrator("admin-7");
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

        admin.suspendShop(new SuspendShopCommand(approved.shopId(), "违规商品", approved.rowVersion()));
        ShopView suspended = seller.getOwnedShop("owner-token");

        assertThat(suspended.status()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo("违规商品");
        assertThat(suspended.suspendedByUserId()).isEqualTo("admin-7");
        assertThat(suspended.suspendedAt()).isEqualTo(Instant.parse("2026-08-28T06:30:00Z"));
        assertThat(sellerApplications.getMyApplication("owner-token").status())
                .isEqualTo(SellerApplicationStatus.APPROVED);

        admin.resumeShop(new ResumeShopCommand(suspended.shopId(), suspended.rowVersion()));
        ShopView resumed = seller.requireOwnedActiveShop("owner-token");
        assertThat(resumed.status()).isEqualTo(ShopStatus.ACTIVE);
        assertThat(resumed.suspensionReason()).isEqualTo("违规商品");
        assertThat(resumed.suspendedByUserId()).isEqualTo("admin-7");
    }

    @Test
    void suspensionRequiresReasonAndRejectsStaleVersion() {
        ShopView approved = approveOwnerShop();
        assertThatThrownBy(() -> admin.suspendShop(
                new SuspendShopCommand(approved.shopId(), " ", approved.rowVersion())))
                .isInstanceOf(IllegalArgumentException.class);

        admin.suspendShop(new SuspendShopCommand(approved.shopId(), "违规", approved.rowVersion()));
        assertThatThrownBy(() -> admin.resumeShop(
                new ResumeShopCommand(approved.shopId(), approved.rowVersion())))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_STATUS_INVALID));
    }

    private ShopView approveOwnerShop() {
        var draft = sellerApplications.saveDraft("owner-token", new SaveSellerDraftCommand(
                null, "教师书屋", "教材与文具", "图书", "owner@example.edu", 0));
        var pending = sellerApplications.submitApplication("owner-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        admin.reviewApplication(new ReviewSellerApplicationCommand(pending.applicationId(),
                SellerReviewDecision.APPROVE, null, pending.rowVersion()));
        return seller.getOwnedShop("owner-token");
    }
}
