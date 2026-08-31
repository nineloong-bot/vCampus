package edu.seu.vcampus.server.shop.service;

import edu.seu.vcampus.common.shop.ChangeProductStatusCommand;
import edu.seu.vcampus.common.shop.CreateProductCommand;
import edu.seu.vcampus.common.shop.CreateSkuCommand;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerReviewDecision;
import edu.seu.vcampus.common.shop.ShopErrorCode;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.common.shop.UpdateProductCommand;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductServiceTest {
    private ShopTestDatabase database;
    private SellerApplicationService applications;
    private ShopAdminService admin;
    private ProductService products;

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
        var clock = Clock.fixed(Instant.parse("2026-08-28T07:00:00Z"), ZoneOffset.UTC);
        applications = new SellerApplicationService(repository, users, transactions, locks, clock);
        admin = new ShopAdminService(repository, users, transactions, locks, clock);
        products = new ProductService(repository, users, transactions, locks, clock);
        approve("owner-token", "店铺一");
        approve("stranger-token", "店铺二");
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
    }

    @Test
    void ownerCreatesProductWithExactMoneyAndCanActivateIt() {
        var created = products.createProduct("owner-token", new CreateProductCommand(
                "签字笔", "文具", "顺滑书写", List.of(
                        new CreateSkuCommand("黑色", new BigDecimal("2.50"), 20, true))));

        assertThat(created.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(created.skus()).singleElement().satisfies(sku -> {
            assertThat(sku.unitPrice()).isEqualByComparingTo("2.50");
            assertThat(sku.availableQuantity()).isEqualTo(20);
        });

        products.changeProductStatus("owner-token", new ChangeProductStatusCommand(
                created.productId(), ProductStatus.ACTIVE, created.rowVersion()));
    }

    @Test
    void anotherShopOwnerCannotUpdateTheProduct() {
        var created = products.createProduct("owner-token", new CreateProductCommand(
                "笔记本", "文具", "横线本", List.of(
                        new CreateSkuCommand("A5", new BigDecimal("8.00"), 5, true))));

        assertThatThrownBy(() -> products.updateProduct("stranger-token",
                new UpdateProductCommand(created.productId(), "越权修改", created.category(),
                        created.description(), List.of(), created.rowVersion())))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_NOT_OWNER));
    }

    @Test
    void rejectsDuplicateGenericProductNameWithinOneShop() {
        products.createProduct("owner-token", product("中性笔", "https://img.example/a.png"));

        assertThatThrownBy(() -> products.createProduct("owner-token",
                product("  中性笔  ", "https://img.example/b.png")))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_PRODUCT_NAME_EXISTS));
    }

    @Test
    void rejectsNonHttpsOrCredentialedCoverUrl() {
        assertThatThrownBy(() -> products.createProduct("owner-token",
                product("中性笔", "http://example.test/pen.png")))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_COVER_IMAGE_URL_INVALID));
        assertThatThrownBy(() -> products.createProduct("owner-token",
                product("铅笔", "https://user:password@example.test/pen.png")))
                .isInstanceOfSatisfying(ShopException.class, error -> assertThat(error.code())
                        .isEqualTo(ShopErrorCode.SHOP_COVER_IMAGE_URL_INVALID));
    }

    private static CreateProductCommand product(String name, String coverImageUrl) {
        return new CreateProductCommand(name, "文具", "顺滑书写", coverImageUrl, List.of(
                new CreateSkuCommand("黑色", new BigDecimal("2.50"), 20, true)));
    }

    private void approve(String token, String shopName) {
        var draft = applications.saveDraft(token, new SaveSellerDraftCommand(
                null, shopName, "简介", "文具", "contact@example.edu", "经营计划", 0));
        var pending = applications.submitApplication(token,
                new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion()));
        admin.reviewApplication("admin-token", new ReviewSellerApplicationCommand(pending.applicationId(),
                SellerReviewDecision.APPROVE, null, pending.rowVersion()));
    }
}
