package edu.seu.vcampus.client.shop.demo;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.shop.service.ShopClientException;
import edu.seu.vcampus.client.shop.service.ShopClientService;
import edu.seu.vcampus.client.user.service.UserClientService;
import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.server.shop.demo.ShopAuthDemoDatabase;
import edu.seu.vcampus.server.shop.demo.ShopAuthDemoRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopSellerAdminEndToEndTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    @TempDir Path temp;

    @Test
    void buyerApplicationCanBeApprovedIntoAnActiveOwnedShop() throws Exception {
        Path database = database("approval");
        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                Session buyer = login(runtime, "DEMO_BUYER");
                Session admin = login(runtime, "DEMO_ADMIN")) {
            ShopView shop = approveNewShop(buyer.shop(), admin.shop(),
                    "学生实践店", "文具");

            assertThat(shop.status()).isEqualTo(ShopStatus.ACTIVE);
            assertThat(shop.ownerUserId()).isEqualTo("demo-buyer");
            assertThat(buyer.shop().getMyApplication().join()).hasValueSatisfying(application ->
                    assertThat(application.status()).isEqualTo(SellerApplicationStatus.APPROVED));
        }
    }

    @Test
    void teacherEditsRejectedApplicationAndReceivesNewRejectionReason() throws Exception {
        Path database = database("teacher-resubmit");
        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                Session teacher = login(runtime, "DEMO_TEACHER");
                Session admin = login(runtime, "DEMO_ADMIN")) {
            SellerApplicationView rejected = teacher.shop().getMyApplication().join().orElseThrow();
            SellerApplicationView draft = teacher.shop().saveApplication(new SaveSellerDraftCommand(
                    rejected.applicationId(), rejected.shopName(), rejected.description(),
                    rejected.category(), rejected.contact(), "补充周一至周五经营及七天售后",
                    rejected.rowVersion())).join();
            SellerApplicationView pending = teacher.shop().submitApplication(
                    new SubmitSellerApplicationCommand(draft.applicationId(), draft.rowVersion())).join();
            admin.shop().reviewApplication(new ReviewSellerApplicationCommand(
                    pending.applicationId(), SellerReviewDecision.REJECT,
                    "请进一步补充供应商信息", pending.rowVersion())).join();

            assertThat(teacher.shop().getMyApplication().join()).hasValueSatisfying(application -> {
                assertThat(application.status()).isEqualTo(SellerApplicationStatus.REJECTED);
                assertThat(application.reviewReason()).isEqualTo("请进一步补充供应商信息");
                assertThat(application.applicationStatement()).contains("七天售后");
            });
        }
    }

    @Test
    void approvedOwnerCreatesAndActivatesOneGenericProductWithTwoSkus() throws Exception {
        Path database = database("seller-product");
        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                Session owner = login(runtime, "DEMO_BUYER");
                Session admin = login(runtime, "DEMO_ADMIN")) {
            approveNewShop(owner.shop(), admin.shop(), "学生文具实践店", "文具");
            ProductView active = createActiveProduct(owner.shop(), "演示中性笔");

            assertThat(active.skus()).extracting(ProductSkuView::skuName)
                    .containsExactlyInAnyOrder("黑色 0.5mm", "蓝色 0.5mm");
            assertThat(owner.shop().searchOwnedProducts(new ProductManagementQuery(
                    null, null, "演示中性笔", 0, 20)).join().items())
                    .singleElement().extracting(ProductManagementSummary::productId)
                    .isEqualTo(active.productId());
            assertThat(owner.shop().search(new ProductSearchQuery("演示中性笔", "文具",
                    null, null, ProductSortMode.SALES_DESC, 0, 20)).join().items())
                    .singleElement().extracting(ProductSummary::productId)
                    .isEqualTo(active.productId());
        }
    }

    @Test
    void ownerCannotBuyOwnSkuButOtherBuyerPaysAndSellerSeesOrder() throws Exception {
        Path database = database("self-purchase");
        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                Session owner = login(runtime, "DEMO_BUYER");
                Session other = login(runtime, "DEMO_OTHER_BUYER");
                Session admin = login(runtime, "DEMO_ADMIN")) {
            approveNewShop(owner.shop(), admin.shop(), "学生交易实践店", "文具");
            ProductView product = createActiveProduct(owner.shop(), "交易中性笔");
            String skuId = product.skus().getFirst().skuId();

            assertCode(() -> owner.shop().addToCart(new AddCartItemCommand(skuId, 1)).join(),
                    "SHOP_SELF_PURCHASE_FORBIDDEN");
            CartView cart = other.shop().addToCart(new AddCartItemCommand(skuId, 2)).join();
            CheckoutResult checkout = other.shop().checkout(new CheckoutCommand(
                    cart.items().stream().map(item -> new CheckoutItem(
                            item.cartItemId(), item.displayedUnitPrice())).toList(), false)).join();
            other.shop().simulatePayment(new SimulatePaymentCommand(checkout.paymentId(),
                    PaymentChannel.ALIPAY, PaymentAttemptStatus.SUCCEEDED)).join();

            assertThat(owner.shop().getOwnedOrders(new SellerOrderQuery(
                    OrderStatus.PAID, 0, 20)).join().orders())
                    .anySatisfy(order -> assertThat(order.items())
                            .anySatisfy(item -> assertThat(item.productId())
                                    .isEqualTo(product.productId())));
        }
    }

    @Test
    void suspensionHidesCatalogAndBlocksWritesWhileReadsRemainThenResumeWorks() throws Exception {
        Path database = database("suspension");
        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                Session owner = login(runtime, "DEMO_BUYER");
                Session admin = login(runtime, "DEMO_ADMIN")) {
            ShopView shop = approveNewShop(owner.shop(), admin.shop(), "学生停业演示店", "文具");
            createActiveProduct(owner.shop(), "停业测试商品");
            admin.shop().suspendShop(new SuspendShopCommand(
                    shop.shopId(), "Demo 停业检查", shop.rowVersion())).join();

            assertThat(owner.shop().searchOwnedProducts(new ProductManagementQuery(
                    null, null, null, 0, 20)).join().items()).isNotEmpty();
            assertCode(() -> owner.shop().createOwnedProduct(product("停业期间新品")).join(),
                    "SHOP_SUSPENDED");
            assertThat(owner.shop().search(new ProductSearchQuery("停业测试商品", null,
                    null, null, ProductSortMode.SALES_DESC, 0, 20)).join().items()).isEmpty();

            ShopAdminSummary suspended = admin.shop().searchShops(new ShopAdminQuery(
                    "学生停业演示店", ShopStatus.SUSPENDED, 0, 20)).join().items().getFirst();
            admin.shop().resumeShop(new ResumeShopCommand(
                    suspended.shopId(), suspended.rowVersion())).join();
            assertThat(owner.shop().createOwnedProduct(product("复业新品")).join().productName())
                    .isEqualTo("复业新品");
        }
    }

    @Test
    void administratorEditsSelectedShopSkuAndRemainsUnableToBuy() throws Exception {
        Path database = database("admin-product");
        try (ShopAuthDemoRuntime runtime = ShopAuthDemoRuntime.start(database, 0);
                Session admin = login(runtime, "DEMO_ADMIN")) {
            ProductView stored = admin.shop().getProduct(new AdminProductRef(
                    "demo-shop-stationery", "demo-stationery-001")).join();
            List<UpsertSkuCommand> skus = stored.skus().stream().map(sku ->
                    new UpsertSkuCommand(sku.skuId(), sku.skuName(),
                            new BigDecimal("9.99"), sku.stockQuantity() + 1,
                            sku.active(), sku.rowVersion())).toList();
            ProductView updated = admin.shop().updateProduct(new AdminUpdateProductCommand(
                    "demo-shop-stationery", new UpdateProductCommand(stored.productId(),
                            stored.productName(), stored.category(), stored.description(), null,
                            skus, stored.rowVersion()))).join();

            assertThat(updated.skus()).allSatisfy(sku -> {
                assertThat(sku.unitPrice()).isEqualByComparingTo("9.99");
                assertThat(sku.stockQuantity()).isPositive();
            });
            assertCode(() -> admin.shop().addToCart(new AddCartItemCommand(
                    updated.skus().getFirst().skuId(), 1)).join(), "SHOP_BUYER_FORBIDDEN");
        }
    }

    private Path database(String name) throws Exception {
        Path database = temp.resolve(name + ".accdb");
        ShopAuthDemoDatabase.initialize(database, schemaDir(), seedDir());
        return database;
    }

    private static ShopView approveNewShop(ShopClientService owner, ShopClientService admin,
            String shopName, String category) {
        SellerApplicationView draft = owner.saveApplication(new SaveSellerDraftCommand(
                null, shopName, "Demo 申请店铺", category, "demo@example.com",
                "用于最终发布验收", 0)).join();
        SellerApplicationView pending = owner.submitApplication(new SubmitSellerApplicationCommand(
                draft.applicationId(), draft.rowVersion())).join();
        admin.reviewApplication(new ReviewSellerApplicationCommand(pending.applicationId(),
                SellerReviewDecision.APPROVE, null, pending.rowVersion())).join();
        return owner.getOwnedShop().join();
    }

    private static ProductView createActiveProduct(ShopClientService owner, String name) {
        ProductView draft = owner.createOwnedProduct(product(name)).join();
        owner.changeOwnedProductStatus(new ChangeProductStatusCommand(draft.productId(),
                ProductStatus.ACTIVE, draft.rowVersion())).join();
        return owner.getOwnedProduct(draft.productId()).join();
    }

    private static CreateProductCommand product(String name) {
        return new CreateProductCommand(name, "文具", "Demo 多规格商品", null, List.of(
                new CreateSkuCommand("黑色 0.5mm", new BigDecimal("2.50"), 20, true),
                new CreateSkuCommand("蓝色 0.5mm", new BigDecimal("2.70"), 18, true)));
    }

    private static Session login(ShopAuthDemoRuntime runtime, String loginId) throws Exception {
        ClientConnection connection = new ClientConnection("127.0.0.1", runtime.localPort());
        connection.connect(TIMEOUT);
        new UserClientService(connection, loginId + "-workflow", TIMEOUT)
                .login(loginId, "123456".toCharArray()).join();
        return new Session(connection, new ShopClientService(connection, TIMEOUT));
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).hasRootCauseInstanceOf(ShopClientException.class)
                .rootCause().extracting("code").isEqualTo(code);
    }

    private static Path schemaDir() { return projectDirectory("schema"); }
    private static Path seedDir() { return projectDirectory("seed"); }
    private static Path projectDirectory(String name) {
        Path fromModule = Path.of("..", "vcampus-database", name);
        return Files.isDirectory(fromModule) ? fromModule : Path.of("vcampus-database", name);
    }

    private record Session(ClientConnection connection, ShopClientService shop)
            implements AutoCloseable {
        @Override public void close() { connection.close(); }
    }
}
