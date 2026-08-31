package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.server.concurrency.StripedResourceLockManager;
import edu.seu.vcampus.server.persistence.TransactionManager;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.repository.AccessShopRepository;
import edu.seu.vcampus.server.shop.testutil.FakeShopUserPort;
import edu.seu.vcampus.server.shop.testutil.ShopTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminProductServiceTest {
    private ShopTestDatabase database;
    private AdminProductService products;
    private String shopId;

    @BeforeEach void setUp() throws Exception {
        database = new ShopTestDatabase();
        var repository = new AccessShopRepository();
        var users = new FakeShopUserPort();
        users.add("owner-token", "owner-1", ShopUserKind.TEACHER, true);
        users.add("student-token", "student-1", ShopUserKind.STUDENT, true);
        users.add("admin-token", "admin-1", ShopUserKind.ADMINISTRATOR, true);
        var transactions = new TransactionManager(database.connections());
        var locks = new StripedResourceLockManager();
        var clock = Clock.fixed(Instant.parse("2026-08-28T08:00:00Z"), ZoneOffset.UTC);
        var applications = new SellerApplicationService(repository, users, transactions, locks, clock);
        var governance = new ShopAdminService(repository, users, transactions, locks, clock);
        var draft = applications.saveDraft("owner-token", new SaveSellerDraftCommand(null,
                "目标店铺", "简介", "文具", "contact", "经营计划", 0));
        var pending = applications.submitApplication("owner-token",
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        governance.reviewApplication("admin-token", new ReviewSellerApplicationCommand(
                pending.applicationId(), SellerReviewDecision.APPROVE, null, pending.rowVersion()));
        shopId = new SellerService(repository, users, transactions)
                .getOwnedShop("owner-token").shopId();
        products = new AdminProductService(repository, users, transactions, locks, clock,
                new ShopBusinessLogger());
    }

    @AfterEach void tearDown() throws Exception { database.close(); }

    @Test
    void activeAdministratorCreatesForSelectedShopAndInheritsItsCategory() {
        ProductView created = products.createProduct("admin-token", new AdminCreateProductCommand(
                shopId, new CreateProductCommand("管理员商品", "药品", "说明", null, List.of(
                        new CreateSkuCommand("标准", new BigDecimal("8.00"), 5, true)))));

        assertThat(created.category()).isEqualTo("文具");
        assertThat(products.searchProducts("admin-token", new ProductManagementQuery(
                shopId, null, null, 0, 20)).items())
                .extracting(ProductManagementSummary::productId).containsExactly(created.productId());
    }

    @Test
    void administratorLoadsCompleteProductFromExplicitlySelectedShop() {
        ProductView created = products.createProduct("admin-token", new AdminCreateProductCommand(
                shopId, new CreateProductCommand("可编辑商品", "其他", "说明", null, List.of(
                        new CreateSkuCommand("标准", new BigDecimal("8.00"), 5, true)))));

        ProductView loaded = products.getProduct("admin-token",
                new AdminProductRef(shopId, created.productId()));

        assertThat(loaded.productId()).isEqualTo(created.productId());
        assertThat(loaded.skus()).singleElement().satisfies(sku -> {
            assertThat(sku.skuId()).isNotBlank();
            assertThat(sku.stockQuantity()).isEqualTo(5);
        });
    }

    @Test
    void regularUserCannotManageAnotherShop() {
        assertThatThrownBy(() -> products.searchProducts("student-token",
                new ProductManagementQuery(shopId, null, null, 0, 20)))
                .isInstanceOf(ShopAccessException.class);
    }
}
