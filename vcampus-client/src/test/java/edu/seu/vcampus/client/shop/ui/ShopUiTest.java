package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.navigation.PageNavigator;
import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.ShopClientFixtures;
import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.CheckoutPanelTestSeam;
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
import javax.swing.WindowConstants;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ShopUiTest {
    @Test
    void disposeInvalidatesSixPendingPagesBeforeAnyCompletionCanMutateUiOrSession() throws Exception {
        PendingClient client = new PendingClient();
        StateCountingKit uiKit = new StateCountingKit();
        AtomicInteger expired = new AtomicInteger();
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                new PageNavigator(new JPanel()), client, uiKit, expired::incrementAndGet));

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(defaultHome())));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(defaultSearch())));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product")));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Storefront("shop")));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Cart()));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        int stateCallsBeforeDispose = uiKit.stateCalls.get();
        List<ShopRoute> historyBeforeDispose = coordinator.navigator().history();
        ShopRoute currentBeforeDispose = coordinator.navigator().current().orElseThrow();

        onEdt(coordinator::dispose);
        onEdt(coordinator::dispose);
        client.failEveryPendingRequest();
        flushEdt();

        assertThat(uiKit.stateCalls).hasValue(stateCallsBeforeDispose);
        assertThat(expired).hasValue(0);
        assertThat(coordinator.navigator().history()).isEqualTo(historyBeforeDispose);
        assertThat(coordinator.navigator().current()).contains(currentBeforeDispose);
    }

    @Test
    void realBuyerPageSetPassesCallbackIdentityToEveryPageAndDisposesActiveCashierOnce()
            throws Exception {
        CheckoutSuccessClient client = new CheckoutSuccessClient();
        AtomicReference<CheckoutPanelTestSeam.Fixture> checkout = new AtomicReference<>();
        List<CallbackCapture> captures = new ArrayList<>();
        Runnable sessionExpired = () -> { };
        ShopPageCoordinator.BuyerPageFactory factory = new ShopPageCoordinator.BuyerPageFactory(
                client, (factoryClient, navigator, uiKit, callback) -> {
                    CheckoutPanelTestSeam.Fixture fixture = CheckoutPanelTestSeam.create(
                            factoryClient, navigator, uiKit, callback);
                    checkout.set(fixture);
                    return fixture.panel();
                }, (page, callback) -> captures.add(new CallbackCapture(page, callback)));
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                new PageNavigator(new JPanel()), factory, new DefaultShopUiKit(), sessionExpired));

        assertThat(captures).extracting(CallbackCapture::page).containsExactly(
                "home", "search", "product", "storefront", "cart", "checkout");
        assertThat(captures).allSatisfy(capture ->
                assertThat(capture.callback()).isSameAs(sessionExpired));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        flushEdt();
        onEdt(() -> checkout.get().panel().submit());
        flushEdt();

        assertThat(checkout.get().cashierSessionExpired().get()).isSameAs(sessionExpired);
        onEdt(coordinator::dispose);
        onEdt(coordinator::dispose);

        assertThat(checkout.get().cashierDisposals()).hasValue(1);
    }

    @Test
    void coordinatorRegistersFixedPagesAndRendersEveryRouteWithItsExactPayload() throws Exception {
        RecordingClient client = new RecordingClient();
        JPanel content = onEdt(() -> new JPanel());
        PageNavigator pages = onEdt(() -> new PageNavigator(content));
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                pages, client, new DefaultShopUiKit(), () -> { }));
        List<Component> fixedCards = Arrays.asList(content.getComponents());
        assertThat(fixedCards).hasSize(7).extracting(Component::getName).containsExactly(
                "shop.home", "shop.search", "shop.product", "shop.storefront", "shop.cart",
                "shop.checkout", "shop.payment-result");
        HomeProductQuery home = new HomeProductQuery(new BigDecimal("1.00"), new BigDecimal("9.00"),
                ProductSortMode.PRICE_DESC, 4, 8);
        ProductSearchQuery search = new ProductSearchQuery("本", "文具", new BigDecimal("1.00"),
                new BigDecimal("9.00"), ProductSortMode.SALES_DESC, 2, 6);
        PaymentView payment = payment();

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(home)));
        assertThat(client.homeQueries).containsExactly(home);
        assertVisible(content, "shop.home");
        assertFixedCards(content, fixedCards);
        JPanel homePage = component(content, "shop.home", JPanel.class);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(search)));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(search)));
        assertThat(client.searchQueries).containsExactly(search);
        assertThat(coordinator.navigator().history()).containsExactly(new ShopRoute.Home(home));
        assertVisible(content, "shop.search");
        assertFixedCards(content, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-7")));
        assertThat(client.productIds).containsExactly("product-7");
        assertVisible(content, "shop.product");
        assertFixedCards(content, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Storefront("shop-9")));
        assertThat(client.shopIds).containsExactly("shop-9");
        assertVisible(content, "shop.storefront");
        assertFixedCards(content, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Cart()));
        assertThat(client.cartLoads).isEqualTo(1);
        assertVisible(content, "shop.cart");
        assertFixedCards(content, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        assertThat(client.cartLoads).isEqualTo(2);
        assertVisible(content, "shop.checkout");
        assertFixedCards(content, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.PaymentResult(payment)));
        assertVisible(content, "shop.payment-result");
        assertThat(component(content, "payment-number", JLabel.class).getText()).isEqualTo("P0007");
        assertFixedCards(content, fixedCards);

        HomeProductQuery refreshedHome = new HomeProductQuery(null, new BigDecimal("12.00"),
                ProductSortMode.SALES_DESC, 1, 5);
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(refreshedHome)));
        assertThat(client.homeQueries).containsExactly(home, refreshedHome);
        assertThat(component(content, "shop.home", JPanel.class)).isSameAs(homePage);
        assertVisible(content, "shop.home");
        assertFixedCards(content, fixedCards);

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
    void installerWindowClosingDisposesTheSameCoordinatorOnce() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        MainFrame frame = onEdt(MainFrame::new);
        RecordingInstalledCoordinator coordinator = new RecordingInstalledCoordinator();
        onEdt(() -> frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE));

        onEdt(() -> ShopUiInstaller.install(frame, new RecordingClient(), new DefaultShopUiKit(),
                () -> { }, (pages, client, uiKit, sessionExpired) -> coordinator));
        onEdt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        flushEdt();

        assertThat(coordinator.disposals).hasValue(1);
        onEdt(frame::dispose);
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

    private static void assertFixedCards(JPanel content, List<Component> expected) {
        assertThat(Arrays.asList(content.getComponents())).containsExactlyElementsOf(expected);
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

    private static final class PendingClient implements ShopClientPort {
        private final CompletableFuture<PageResult<ProductSummary>> home = new CompletableFuture<>();
        private final CompletableFuture<PageResult<ProductSummary>> search = new CompletableFuture<>();
        private final CompletableFuture<ProductDetail> product = new CompletableFuture<>();
        private final CompletableFuture<ShopDetail> storefront = new CompletableFuture<>();
        private final List<CompletableFuture<CartView>> carts = List.of(
                new CompletableFuture<>(), new CompletableFuture<>());
        private int nextCart;

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return home; }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return search; }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return product; }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return storefront; }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return carts.get(nextCart++); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }

        private void failEveryPendingRequest() {
            IllegalStateException expired = new IllegalStateException("AUTH_SESSION_EXPIRED");
            home.completeExceptionally(expired);
            search.completeExceptionally(expired);
            product.completeExceptionally(expired);
            storefront.completeExceptionally(expired);
            carts.forEach(future -> future.completeExceptionally(expired));
        }
    }

    private static final class StateCountingKit implements ShopUiKit {
        private final ShopUiKit delegate = new DefaultShopUiKit();
        private final AtomicInteger stateCalls = new AtomicInteger();

        @Override public JButton navigationButton(String name, String text) { return delegate.navigationButton(name, text); }
        @Override public JButton primaryButton(String name, String text) { return delegate.primaryButton(name, text); }
        @Override public JButton secondaryButton(String name, String text) { return delegate.secondaryButton(name, text); }
        @Override public JPanel filterPanel(String name, LayoutManager layout) { return delegate.filterPanel(name, layout); }
        @Override public JPanel productCard(String name, LayoutManager layout) { return delegate.productCard(name, layout); }
        @Override public JComponent stateView(String name, ShopPageState state, String message, Runnable retry) {
            stateCalls.incrementAndGet();
            return delegate.stateView(name, state, message, retry);
        }
    }

    private static final class RecordingInstalledCoordinator implements ShopUiInstaller.InstalledCoordinator {
        private final AtomicInteger disposals = new AtomicInteger();
        private final edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator navigator =
                new edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator(route -> { });

        @Override public edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator navigator() { return navigator; }
        @Override public void dispose() { disposals.incrementAndGet(); }
    }

    private record CallbackCapture(String page, Runnable callback) { }

    private static final class CheckoutSuccessClient implements ShopClientPort {
        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return CompletableFuture.completedFuture(ShopClientFixtures.cartView()); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) {
            return CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult());
        }
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
