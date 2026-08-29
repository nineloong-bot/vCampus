package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ShopUiTest {
    @Test
    void coordinatorRegistersFixedPagesAndRendersEveryRouteWithItsExactPayload() throws Exception {
        RecordingClient client = new RecordingClient();
        JPanel content = onEdt(() -> new JPanel());
        PageNavigator pages = onEdt(() -> new PageNavigator(content));
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                pages, client, new DefaultShopUiKit(), () -> { }));
        HomeProductQuery home = new HomeProductQuery(new BigDecimal("1.00"), new BigDecimal("9.00"),
                ProductSortMode.PRICE_DESC, 4, 8);
        ProductSearchQuery search = new ProductSearchQuery("本", "文具", new BigDecimal("1.00"),
                new BigDecimal("9.00"), ProductSortMode.SALES_DESC, 2, 6);
        PaymentView payment = payment();

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(home)));
        assertThat(client.homeQueries).containsExactly(home);
        assertVisible(content, "shop.home");
        JPanel homePage = component(content, "shop.home", JPanel.class);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(search)));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(search)));
        assertThat(client.searchQueries).containsExactly(search);
        assertThat(coordinator.navigator().history()).containsExactly(new ShopRoute.Home(home));
        assertVisible(content, "shop.search");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-7")));
        assertThat(client.productIds).containsExactly("product-7");
        assertVisible(content, "shop.product");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Storefront("shop-9")));
        assertThat(client.shopIds).containsExactly("shop-9");
        assertVisible(content, "shop.storefront");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Cart()));
        assertThat(client.cartLoads).isEqualTo(1);
        assertVisible(content, "shop.cart");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        assertThat(client.cartLoads).isEqualTo(2);
        assertVisible(content, "shop.checkout");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.PaymentResult(payment)));
        assertVisible(content, "shop.payment-result");
        assertThat(component(content, "payment-number", JLabel.class).getText()).isEqualTo("P0007");

        HomeProductQuery refreshedHome = new HomeProductQuery(null, new BigDecimal("12.00"),
                ProductSortMode.SALES_DESC, 1, 5);
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(refreshedHome)));
        assertThat(client.homeQueries).containsExactly(home, refreshedHome);
        assertThat(component(content, "shop.home", JPanel.class)).isSameAs(homePage);
        assertVisible(content, "shop.home");

        onEdt(coordinator::dispose);
        onEdt(coordinator::dispose);
        flushEdt();
    }

    @Test
    void installsOneShopEntryAndRendersHomeWithoutChangingMainFrame() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        RecordingClient client = new RecordingClient();
        NavigationCountingKit uiKit = new NavigationCountingKit();
        MainFrame frame = onEdt(MainFrame::new);

        onEdt(() -> ShopUiInstaller.install(frame, client, uiKit, () -> { }));
        JButton shop = component(frame.navigation(), "shop.navigation", JButton.class);
        onEdt(() -> shop.doClick());
        flushEdt();

        assertThat(shop.getText()).isEqualTo("校园商城");
        assertThat(uiKit.navigationButtons).containsExactly("shop.navigation");
        assertThat(uiKit.primaryButtons).doesNotContain("shop.navigation");
        assertThat(client.homeQueries).containsExactly(new HomeProductQuery(null, null,
                ProductSortMode.SALES_DESC, 0, 20));
        assertVisible(frame.content(), "shop.home");
    }

    @Test
    void passesTheSameSessionExpiredCallbackToEveryAsynchronousFixedPage() throws Exception {
        ExpiringClient client = new ExpiringClient();
        AtomicInteger expired = new AtomicInteger();
        Runnable sessionExpired = expired::incrementAndGet;
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                new PageNavigator(new JPanel()), client, new DefaultShopUiKit(), sessionExpired));

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(defaultHome())));
        client.home.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(defaultSearch())));
        client.search.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product")));
        client.product.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Storefront("shop")));
        client.storefront.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Cart()));
        client.cart.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        client.checkoutCart.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();

        assertThat(expired).hasValue(6);
        onEdt(coordinator::dispose);
    }

    private static void assertVisible(JPanel content, String name) {
        assertThat(component(content, name, JPanel.class).isVisible()).isTrue();
    }

    private static PaymentView payment() {
        return new PaymentView("payment-7", "group-7", "P0007", new BigDecimal("7.00"),
                PaymentStatus.SUCCEEDED, PaymentChannel.WECHAT,
                Instant.parse("2026-08-30T00:00:00Z"), null, 0);
    }

    private static HomeProductQuery defaultHome() {
        return new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20);
    }

    private static ProductSearchQuery defaultSearch() {
        return new ProductSearchQuery(null, null, null, null, ProductSortMode.SALES_DESC, 0, 20);
    }

    private static final class RecordingClient implements ShopClientPort {
        private final List<HomeProductQuery> homeQueries = new ArrayList<>();
        private final List<ProductSearchQuery> searchQueries = new ArrayList<>();
        private final List<String> productIds = new ArrayList<>();
        private final List<String> shopIds = new ArrayList<>();
        private int cartLoads;

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
            homeQueries.add(query); return new CompletableFuture<>();
        }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) {
            searchQueries.add(query); return new CompletableFuture<>();
        }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
            productIds.add(productId); return new CompletableFuture<>();
        }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) {
            shopIds.add(shopId); return new CompletableFuture<>();
        }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) {
            return new CompletableFuture<>();
        }
        @Override public CompletableFuture<CartView> getCart() {
            cartLoads++; return new CompletableFuture<>();
        }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) {
            return new CompletableFuture<>();
        }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) {
            return new CompletableFuture<>();
        }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) {
            return new CompletableFuture<>();
        }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) {
            return new CompletableFuture<>();
        }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) {
            return new CompletableFuture<>();
        }
    }

    private static final class ExpiringClient implements ShopClientPort {
        private final CompletableFuture<PageResult<ProductSummary>> home = new CompletableFuture<>();
        private final CompletableFuture<PageResult<ProductSummary>> search = new CompletableFuture<>();
        private final CompletableFuture<ProductDetail> product = new CompletableFuture<>();
        private final CompletableFuture<ShopDetail> storefront = new CompletableFuture<>();
        private final CompletableFuture<CartView> cart = new CompletableFuture<>();
        private final CompletableFuture<CartView> checkoutCart = new CompletableFuture<>();

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return home; }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return search; }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return product; }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return storefront; }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() {
            return cart.isDone() ? checkoutCart : cart;
        }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }
    }

    private static final class NavigationCountingKit implements ShopUiKit {
        private final ShopUiKit delegate = new DefaultShopUiKit();
        private final List<String> navigationButtons = new ArrayList<>();
        private final List<String> primaryButtons = new ArrayList<>();

        @Override public JButton navigationButton(String name, String text) {
            navigationButtons.add(name);
            return delegate.navigationButton(name, text);
        }
        @Override public JButton primaryButton(String name, String text) {
            primaryButtons.add(name);
            return delegate.primaryButton(name, text);
        }
        @Override public JButton secondaryButton(String name, String text) {
            return delegate.secondaryButton(name, text);
        }
        @Override public JPanel filterPanel(String name, LayoutManager layout) {
            return delegate.filterPanel(name, layout);
        }
        @Override public JPanel productCard(String name, LayoutManager layout) {
            return delegate.productCard(name, layout);
        }
        @Override public JComponent stateView(String name, ShopPageState state, String message,
                Runnable retry) {
            return delegate.stateView(name, state, message, retry);
        }
    }
}
