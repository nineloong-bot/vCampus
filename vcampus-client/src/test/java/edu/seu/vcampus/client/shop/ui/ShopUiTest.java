package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.core.ui.MainFrame;
import edu.seu.vcampus.client.shop.ShopClientFixtures;
import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.CheckoutPanelTestSeam;
import edu.seu.vcampus.client.shop.ui.buyer.BuyerShopPanel;
import edu.seu.vcampus.client.shop.ui.buyer.CartPanel;
import edu.seu.vcampus.client.shop.ui.buyer.MyShopPanel;
import edu.seu.vcampus.client.shop.ui.buyer.PaymentResultPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductDetailPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductSearchPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ShopHomePanel;
import edu.seu.vcampus.client.shop.ui.navigation.HomeViewState;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRouteHost;
import edu.seu.vcampus.client.shop.ui.navigation.StorefrontViewState;
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
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.shop.PaidOrderItemView;
import edu.seu.vcampus.common.shop.PaidOrderView;
import edu.seu.vcampus.common.shop.OrderStatus;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSkuView;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.ShopSummary;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserView;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Cross-page navigation and lifecycle regression suite. */
class ShopUiTest {
    @Test
    void myPageKeepsEveryOrderAndExpandedLastItemReachableInASmallViewport()
            throws Exception {
        MyShopPanel panel = onEdt(() -> new MyShopPanel(buyer(),
                paidOrdersClient(CompletableFuture.completedFuture(scrollingPaidOrders())),
                new DefaultShopUiKit(), () -> { }));
        onEdt(panel::load);
        flushEdt();

        JPanel orders = component(panel, "my.orders", JPanel.class);
        onEdt(() -> layoutAt(panel, 360, 180));
        assertThat(orders.getHeight()).isGreaterThanOrEqualTo(
                orders.getPreferredSize().height);

        JScrollPane scroll = component(panel, "my.orders.scroll", JScrollPane.class);
        JScrollBar vertical = scroll.getVerticalScrollBar();
        int collapsedMaximum = vertical.getMaximum();
        int collapsedExtent = vertical.getVisibleAmount();
        assertThat(collapsedMaximum).isGreaterThan(collapsedExtent);
        assertThat(scroll.getViewport().getViewSize().height)
                .isGreaterThan(scroll.getViewport().getExtentSize().height);

        onEdt(() -> component(panel, "my.order.toggle.order-scroll-5", JButton.class)
                .doClick());
        onEdt(() -> layoutAt(panel, 360, 180));
        assertThat(vertical.getMaximum()).isGreaterThan(collapsedMaximum);
        assertThat(vertical.getMaximum()).isGreaterThan(vertical.getVisibleAmount());

        onEdt(() -> vertical.setValue(vertical.getMaximum()));
        JLabel lastItem = component(panel,
                "my.order.item.order-scroll-5.sku-scroll-5-7", JLabel.class);
        JPanel lastOrder = component(panel, "my.order.order-scroll-5", JPanel.class);
        Rectangle visible = scroll.getViewport().getViewRect();
        Rectangle lastOrderBounds = SwingUtilities.convertRectangle(
                lastOrder.getParent(), lastOrder.getBounds(), orders);
        Rectangle lastItemBounds = SwingUtilities.convertRectangle(
                lastItem.getParent(), lastItem.getBounds(), orders);
        assertThat(visible.getMaxY()).isGreaterThanOrEqualTo(lastOrderBounds.getMaxY());
        assertThat(visible.getMaxY()).isGreaterThanOrEqualTo(lastItemBounds.getMaxY());
        assertThat(vertical.getValue())
                .isEqualTo(vertical.getMaximum() - vertical.getVisibleAmount());
    }

    @Test
    void myPageUsesShopIdentityAndExpandsPaidOrdersInServerOrder() throws Exception {
        PaidOrderHistory history = paidOrders();
        ShopClientPort client = paidOrdersClient(CompletableFuture.completedFuture(history));
        MyShopPanel panel = onEdt(() -> new MyShopPanel(
                buyer(), client, new DefaultShopUiKit(), () -> { }));

        onEdt(panel::load);
        flushEdt();

        assertThat(component(panel, "my.title", JLabel.class).getText())
                .isEqualTo("商城个人中心");
        JPanel orders = component(panel, "my.orders", JPanel.class);
        assertThat(Arrays.asList(orders.getComponents()))
                .extracting(Component::getName)
                .containsExactly("my.order.order-new", "my.order.order-old");

        JPanel details = component(panel, "my.order.details.order-new", JPanel.class);
        assertThat(details.isVisible()).isFalse();
        onEdt(() -> component(panel, "my.order.toggle.order-new", JButton.class).doClick());
        assertThat(details.isVisible()).isTrue();
        assertThat(component(panel, "my.order.item.order-new.sku-new", JLabel.class).getText())
                .contains("product-new", "签字笔", "sku-new", "黑色",
                        "2", "3.00", "6.00");
        assertThat(component(panel, "my.order.total.order-new", JLabel.class).getText())
                .isEqualTo("总额 ¥6.00");
        assertThat(component(panel, "my.order.paid-at.order-new", JLabel.class).getText())
                .isEqualTo("支付时间 2026-08-30 10:00:00 Asia/Shanghai");
        assertThat(component(panel, "my.order.status.order-new", JLabel.class).getText())
                .contains("PAID");
        JPanel newestCard = component(panel, "my.order.order-new", JPanel.class);
        assertThat(newestCard.getBorder()).isNotNull();
        assertThat(newestCard.getBorder().getBorderInsets(newestCard).bottom)
                .isGreaterThanOrEqualTo(8);
    }

    @Test
    void myPageRejectsOlderAndDisposedResponsesAndExpiresTheSharedSession() throws Exception {
        CompletableFuture<PaidOrderHistory> old = new CompletableFuture<>();
        CompletableFuture<PaidOrderHistory> latest = new CompletableFuture<>();
        CompletableFuture<PaidOrderHistory> afterDispose = new CompletableFuture<>();
        ShopClientPort client = paidOrdersClient(old, latest, afterDispose);
        StateCountingKit uiKit = new StateCountingKit();
        AtomicInteger expired = new AtomicInteger();
        MyShopPanel panel = onEdt(() -> new MyShopPanel(
                buyer(), client, uiKit, expired::incrementAndGet));

        onEdt(panel::load);
        onEdt(panel::load);
        latest.complete(new PaidOrderHistory(List.of(paidOrders().orders().getFirst())));
        flushEdt();
        old.complete(new PaidOrderHistory(List.of(paidOrders().orders().getLast())));
        flushEdt();
        assertThat(component(panel, "my.orders", JPanel.class).getComponents())
                .extracting(Component::getName)
                .containsExactly("my.order.order-new");

        onEdt(panel::load);
        int stateCallsAtDispose = uiKit.stateCalls.get();
        onEdt(panel::disposePage);
        afterDispose.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        assertThat(uiKit.stateCalls).hasValue(stateCallsAtDispose);
        assertThat(expired).hasValue(0);

        AtomicInteger liveExpired = new AtomicInteger();
        MyShopPanel expiring = onEdt(() -> new MyShopPanel(buyer(),
                paidOrdersClient(CompletableFuture.failedFuture(
                        new IllegalStateException("AUTH_SESSION_EXPIRED"))),
                uiKit, liveExpired::incrementAndGet));
        onEdt(expiring::load);
        flushEdt();
        assertThat(uiKit.states.getLast()).isEqualTo(ShopPageState.DISCONNECTED);
        assertThat(liveExpired).hasValue(1);
    }

    @Test
    void navigatorCapturesExactListStateAndNotifiesBoundedHistoryChanges() {
        HomeProductQuery homeQuery = new HomeProductQuery(null, null,
                ProductSortMode.PRICE_DESC, 2, 20);
        ProductSearchQuery searchQuery = new ProductSearchQuery("签字笔", "文具",
                new BigDecimal("2.00"), new BigDecimal("18.00"),
                ProductSortMode.PRICE_DESC, 3, 10);
        ShopProductQuery shopQuery = new ShopProductQuery("shop-9", "纸", "文具",
                new BigDecimal("1.00"), new BigDecimal("30.00"),
                ProductSortMode.SALES_DESC, 4, 12);
        List<ShopRoute> rendered = new ArrayList<>();
        List<ShopRoute> changes = new ArrayList<>();
        ShopRouteHost host = new ShopRouteHost() {
            @Override public ShopRoute capture(ShopRoute route) {
                return switch (route) {
                    case ShopRoute.Home ignored -> new ShopRoute.Home(
                            new HomeViewState(homeQuery, 360));
                    case ShopRoute.Search ignored -> new ShopRoute.Search(
                            new SearchViewState(searchQuery, true, 420));
                    case ShopRoute.Storefront ignored -> new ShopRoute.Storefront(
                            new StorefrontViewState(shopQuery, 275));
                    default -> route;
                };
            }
            @Override public void render(ShopRoute route) { rendered.add(route); }
        };
        ShopNavigator navigator = new ShopNavigator(host);
        navigator.addListener(changes::add);

        assertThat(navigator.canGoBack()).isFalse();
        navigator.open(new ShopRoute.Home(homeQuery));
        navigator.open(new ShopRoute.Product("product-home"));
        navigator.back();

        assertThat(navigator.current()).contains(new ShopRoute.Home(
                new HomeViewState(homeQuery, 360)));
        assertThat(navigator.canGoBack()).isFalse();

        navigator.reset(new ShopRoute.Search(searchQuery));
        navigator.open(new ShopRoute.Product("product-search"));
        navigator.back();
        assertThat(navigator.current()).contains(new ShopRoute.Search(
                new SearchViewState(searchQuery, true, 420)));

        navigator.reset(new ShopRoute.Storefront(shopQuery));
        navigator.open(new ShopRoute.Product("product-storefront"));
        navigator.back();
        assertThat(navigator.current()).contains(new ShopRoute.Storefront(
                new StorefrontViewState(shopQuery, 275)));

        navigator.open(new ShopRoute.Cart());
        navigator.back();
        navigator.open(new ShopRoute.My());
        navigator.back();
        assertThat(navigator.current()).contains(new ShopRoute.Storefront(
                new StorefrontViewState(shopQuery, 275)));
        assertThat(changes).isNotEmpty();
        assertThat(rendered.getLast()).isEqualTo(navigator.current().orElseThrow());

        navigator.reset(new ShopRoute.Home(homeQuery));
        IntStream.range(0, 25).forEach(index ->
                navigator.open(new ShopRoute.Product("product-" + index)));
        assertThat(navigator.history()).hasSize(20);
    }

    @Test
    void replaceAndResetKeepCompletedCheckoutOutOfEverySafeExit() {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);
        ShopRoute.Home home = new ShopRoute.Home(defaultHome());
        ShopRoute.PaymentResult result = new ShopRoute.PaymentResult(payment());

        navigator.open(home);
        navigator.open(new ShopRoute.Checkout());
        navigator.replaceCurrent(result);
        navigator.back();

        assertThat(navigator.current()).contains(home);
        navigator.reset(result);
        navigator.reset(new ShopRoute.My());
        assertThat(navigator.current()).contains(new ShopRoute.My());
        assertThat(navigator.history()).isEmpty();
        navigator.renderCurrent();
        assertThat(rendered.getLast()).isEqualTo(new ShopRoute.My());
    }

    @Test
    void successfulPaymentButtonsUseApprovedCopyAndClearCheckoutHistory() throws Exception {
        ShopNavigator navigator = new ShopNavigator(route -> { });
        ShopRoute.Home home = new ShopRoute.Home(defaultHome());
        navigator.open(home);
        navigator.open(new ShopRoute.Checkout());
        navigator.replaceCurrent(new ShopRoute.PaymentResult(payment()));
        PaymentResultPanel homeExit = onEdt(() -> new PaymentResultPanel(
                navigator, new DefaultShopUiKit(), payment()));

        assertThat(component(homeExit, "payment-result.home", JButton.class).getText())
                .isEqualTo("继续购物");
        assertThat(component(homeExit, "payment-result.orders", JButton.class).getText())
                .isEqualTo("查看已支付订单");
        onEdt(() -> component(homeExit, "payment-result.home", JButton.class).doClick());
        assertThat(navigator.current()).contains(home);
        assertThat(navigator.history()).isEmpty();
        navigator.back();
        assertThat(navigator.current()).contains(home);

        navigator.open(new ShopRoute.Checkout());
        navigator.replaceCurrent(new ShopRoute.PaymentResult(payment()));
        PaymentResultPanel ordersExit = onEdt(() -> new PaymentResultPanel(
                navigator, new DefaultShopUiKit(), payment()));
        onEdt(() -> component(ordersExit, "payment-result.orders", JButton.class).doClick());
        assertThat(navigator.current()).contains(new ShopRoute.My());
        assertThat(navigator.history()).containsExactly(home);
        navigator.back();
        assertThat(navigator.current()).contains(home);
    }

    @Test
    void coordinatorOrdersReceiptButtonLoadsMyOrdersAndClearsHistory() throws Exception {
        PaidOrdersCountingClient client = new PaidOrdersCountingClient();
        ShopModulePanel content = onEdt(ShopModulePanel::new);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                content, buyer(), client, new DefaultShopUiKit(), () -> { }));

        onEdt(() -> {
            coordinator.navigator().open(new ShopRoute.Home(defaultHome()));
            coordinator.navigator().open(new ShopRoute.Checkout());
            coordinator.navigator().replaceCurrent(new ShopRoute.PaymentResult(payment()));
            component(content, "payment-result.orders", JButton.class).doClick();
        });
        flushEdt();

        assertThat(client.paidOrderLoads).isEqualTo(1);
        assertThat(coordinator.navigator().current()).contains(new ShopRoute.My());
        assertThat(coordinator.navigator().history()).containsExactly(
                new ShopRoute.Home(defaultHome()));
        assertVisible(content, "shop.my");
        assertThat(component(content, "my.order.order-new", JPanel.class).isVisible()).isTrue();
        assertThat(component(content, "shop.back", JButton.class).isEnabled()).isTrue();
        onEdt(() -> component(content, "shop.back", JButton.class).doClick());
        assertThat(coordinator.navigator().current()).contains(new ShopRoute.Home(defaultHome()));
        assertVisible(content, "shop.home");
        onEdt(coordinator::dispose);
    }

    @Test
    void toolbarReflectsEveryRouteAndUsesTheAuthoritativeQuantitySum() throws Exception {
        ShopNavigator navigator = new ShopNavigator(route -> { });
        CartCountModel cartCount = new CartCountModel();
        ShopToolbar toolbar = onEdt(() -> new ShopToolbar(
                navigator, cartCount, new DefaultShopUiKit()));
        assertThat(component(toolbar, "shop.back", JButton.class).getText())
                .isEqualTo("← 返回");
        assertThat(Arrays.stream(component(toolbar, "shop.actions", JPanel.class)
                .getComponents()).map(Component::getName).toList())
                .containsExactly("shop.my", "shop.cart");
        List<ShopRoute> routes = List.of(
                new ShopRoute.Home(defaultHome()),
                new ShopRoute.Search(defaultSearch()),
                new ShopRoute.Product("product-1"),
                new ShopRoute.Storefront("shop-1"),
                new ShopRoute.Cart(),
                new ShopRoute.Checkout(),
                new ShopRoute.PaymentResult(payment()),
                new ShopRoute.My());

        for (ShopRoute route : routes) {
            onEdt(() -> navigator.reset(route));
            assertThat(component(toolbar, "shop.title", JLabel.class).getText()).isNotBlank();
            assertThat(component(toolbar, "shop.back", JButton.class).isVisible()).isTrue();
            assertThat(component(toolbar, "shop.my", JButton.class).isVisible()).isTrue();
            assertThat(component(toolbar, "shop.cart", JButton.class).isVisible())
                    .isEqualTo(!(route instanceof ShopRoute.Search));
        }

        CartView fiveItems = cartWithQuantities(2, 3);
        onEdt(() -> cartCount.update(fiveItems));
        assertThat(component(toolbar, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（5）");

        onEdt(() -> navigator.reset(new ShopRoute.Home(defaultHome())));
        assertThat(component(toolbar, "shop.back", JButton.class).isEnabled()).isFalse();
        onEdt(() -> component(toolbar, "shop.cart", JButton.class).doClick());
        assertThat(navigator.current()).contains(new ShopRoute.Cart());
        onEdt(() -> component(toolbar, "shop.back", JButton.class).doClick());
        assertThat(navigator.current()).contains(new ShopRoute.Home(defaultHome()));
        onEdt(() -> component(toolbar, "shop.my", JButton.class).doClick());
        assertThat(navigator.current()).contains(new ShopRoute.My());
        onEdt(() -> component(toolbar, "shop.back", JButton.class).doClick());
        assertThat(navigator.current()).contains(new ShopRoute.Home(defaultHome()));
    }

    @Test
    void coordinatorRestoresHomeSearchAndStorefrontQueryControlsAndScrollAfterBack()
            throws Exception {
        RestoringClient client = new RestoringClient();
        ShopModulePanel content = onEdt(ShopModulePanel::new);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                content, buyer(), client, new DefaultShopUiKit(), () -> { }));
        HomeProductQuery home = new HomeProductQuery(new BigDecimal("1.00"),
                new BigDecimal("20.00"), ProductSortMode.PRICE_DESC, 2, 8);
        ProductSearchQuery search = new ProductSearchQuery("笔", "文具",
                new BigDecimal("2.00"), new BigDecimal("10.00"),
                ProductSortMode.PRICE_DESC, 3, 6);
        ShopProductQuery storefront = new ShopProductQuery("shop-1", "本", "文具",
                new BigDecimal("3.00"), new BigDecimal("30.00"),
                ProductSortMode.SALES_DESC, 4, 7);

        onEdt(() -> coordinator.navigator().reset(new ShopRoute.Home(home)));
        flushEdt();
        JScrollPane homeScroll = component(content, "home.scroll", JScrollPane.class);
        onEdt(() -> homeScroll.getVerticalScrollBar().setValues(360, 10, 0, 1000));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-1")));
        flushEdt();
        onEdt(coordinator.navigator()::back);
        flushEdt();
        assertThat(client.homeQueries).containsExactly(home, home);
        assertThat(homeScroll.getVerticalScrollBar().getValue()).isEqualTo(360);

        onEdt(() -> coordinator.navigator().reset(new ShopRoute.Search(
                new SearchViewState(search, true, true, 0))));
        flushEdt();
        JScrollPane searchScroll = component(content, "search.scroll", JScrollPane.class);
        onEdt(() -> searchScroll.getVerticalScrollBar().setValues(420, 10, 0, 1000));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-1")));
        flushEdt();
        onEdt(coordinator.navigator()::back);
        flushEdt();
        assertThat(client.searchQueries).containsExactly(search, search);
        assertThat(component(content, "keyword", JTextField.class).getText()).isEqualTo("笔");
        assertThat(component(content, "category", JComboBox.class).getSelectedItem()).isEqualTo("文具");
        assertThat(component(content, "min-price", JTextField.class).getText()).isEqualTo("2.00");
        assertThat(component(content, "max-price", JTextField.class).getText()).isEqualTo("10.00");
        assertThat(component(content, "search.filters", JPanel.class).isVisible()).isTrue();
        assertThat(searchScroll.getVerticalScrollBar().getValue()).isEqualTo(420);

        onEdt(() -> coordinator.navigator().reset(new ShopRoute.Storefront(
                new StorefrontViewState(storefront, 0))));
        flushEdt(); flushEdt();
        JScrollPane storefrontScroll = component(content, "storefront.scroll", JScrollPane.class);
        onEdt(() -> storefrontScroll.getVerticalScrollBar().setValues(275, 10, 0, 1000));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-1")));
        flushEdt();
        onEdt(coordinator.navigator()::back);
        flushEdt(); flushEdt();
        assertThat(client.shopQueries).containsExactly(storefront, storefront);
        assertThat(storefrontScroll.getVerticalScrollBar().getValue()).isEqualTo(275);
        onEdt(coordinator::dispose);
    }

    @Test
    void searchStartsWithOneKeywordFieldAndRevealsOnlyFixedFiltersAfterEmptySuccess()
            throws Exception {
        CompletableFuture<PageResult<ProductSummary>> result = new CompletableFuture<>();
        AtomicReference<ProductSearchPanel> searchPanel = new AtomicReference<>();
        ShopRouteHost host = new ShopRouteHost() {
            @Override public ShopRoute capture(ShopRoute route) {
                if (route instanceof ShopRoute.Search(var state)) {
                    return new ShopRoute.Search(searchPanel.get().capture(state));
                }
                return route;
            }
            @Override public void render(ShopRoute route) {
                if (route instanceof ShopRoute.Search(var state)) {
                    searchPanel.get().search(state);
                }
            }
        };
        ShopNavigator navigator = new ShopNavigator(host);
        ShopClientPort client = searchClient(result);
        ProductSearchPanel panel = onEdt(() -> new ProductSearchPanel(client, navigator,
                new DefaultShopUiKit(), () -> { }));
        searchPanel.set(panel);

        assertThat(component(panel, "keyword", JTextField.class).isVisible()).isTrue();
        assertThat(component(panel, "search", JButton.class).isVisible()).isTrue();
        assertThat(component(panel, "search.filters.toggle", JButton.class).isVisible()).isFalse();
        assertThat(component(panel, "search.filters", JPanel.class).isVisible()).isFalse();

        onEdt(() -> {
            component(panel, "keyword", JTextField.class).setText("  雨伞  ");
            component(panel, "search", JButton.class).doClick();
        });
        assertThat(navigator.current()).contains(new ShopRoute.Search(new SearchViewState(
                new ProductSearchQuery("雨伞", null, null, null,
                        ProductSortMode.SALES_DESC, 0, 20), false, false, 0)));
        assertThat(component(panel, "search.filters.toggle", JButton.class).isVisible()).isFalse();

        result.complete(new PageResult<>(List.of(), 0, 20, 0));
        flushEdt();
        assertThat(component(panel, "search.filters.toggle", JButton.class).isVisible()).isTrue();
        onEdt(() -> component(panel, "search.filters.toggle", JButton.class).doClick());
        assertThat(component(panel, "search.filters", JPanel.class).isVisible()).isTrue();
        JComboBox<?> categories = component(panel, "category", JComboBox.class);
        assertThat(IntStream.range(0, categories.getItemCount())
                .mapToObj(index -> String.valueOf(categories.getItemAt(index))).toList())
                .containsExactly("全部", "文具", "图书", "生活用品", "药品", "其他");
        assertThat(component(panel, "min-price", JTextField.class)).isNotNull();
        assertThat(component(panel, "max-price", JTextField.class)).isNotNull();
        assertThat(component(panel, "sort", JComboBox.class)).isNotNull();
    }

    @Test
    void failedSearchDoesNotRevealFilters() throws Exception {
        CompletableFuture<PageResult<ProductSummary>> failure = new CompletableFuture<>();
        ProductSearchPanel panel = onEdt(() -> new ProductSearchPanel(searchClient(failure),
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.search(new SearchViewState(defaultSearch(), false, false, 0)));
        failure.completeExceptionally(new IllegalStateException("SHOP_UNAVAILABLE"));
        flushEdt();

        assertThat(component(panel, "search.filters.toggle", JButton.class).isVisible()).isFalse();
        assertThat(component(panel, "search.filters", JPanel.class).isVisible()).isFalse();
    }

    @Test
    void homeKeywordSearchOpensAStatefulSearchRoute() throws Exception {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);
        ShopHomePanel home = onEdt(() -> new ShopHomePanel(searchClient(new CompletableFuture<>()),
                navigator, new DefaultShopUiKit(), () -> { }));

        onEdt(() -> {
            component(home, "home.keyword", JTextField.class).setText("  校园书店  ");
            component(home, "home.search", JButton.class).doClick();
        });

        assertThat(navigator.current()).contains(new ShopRoute.Search(new SearchViewState(
                new ProductSearchQuery("校园书店", null, null, null,
                        ProductSortMode.SALES_DESC, 0, 20), false, false, 0)));
    }

    @Test
    void homeOrdersSearchCategoriesAndRecommendationsWithStructuredCategoryRoutesAndCardFields()
            throws Exception {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);
        ProductSummary product = new ProductSummary("product-1", "shop-1", "校园文具店",
                "笔记本", "文具", new BigDecimal("6.50"), 12, Instant.EPOCH);
        ShopHomePanel home = onEdt(() -> new ShopHomePanel(
                homeClient(new PageResult<>(List.of(product), 0, 20, 1)), navigator,
                new DefaultShopUiKit(), () -> { }));

        onEdt(() -> home.load());
        flushEdt();

        JScrollPane scroll = component(home, "home.scroll", JScrollPane.class);
        JPanel content = (JPanel) scroll.getViewport().getView();
        assertThat(Arrays.stream(content.getComponents()).map(Component::getName).toList())
                .containsExactly("home.search-bar", "home.categories", "home.recommendations",
                        "home.results");
        assertThat(component(home, "home.recommendations", JLabel.class).getText()).isEqualTo("猜你喜欢");
        assertThat(component(home, "product-product-1.name", JLabel.class).getText()).isEqualTo("笔记本");
        assertThat(component(home, "product-product-1.shop", JLabel.class).getText()).isEqualTo("校园文具店");
        assertThat(component(home, "product-product-1.category", JLabel.class).getText()).isEqualTo("文具");
        assertThat(component(home, "product-product-1.price", JLabel.class).getText()).isEqualTo("¥6.50 起");
        assertThat(component(home, "product-product-1.sales", JLabel.class).getText()).isEqualTo("销量 12");

        onEdt(() -> {
            component(home, "home.category.文具", JButton.class).doClick();
            component(home, "home.category.图书", JButton.class).doClick();
            component(home, "home.category.生活用品", JButton.class).doClick();
            component(home, "home.category.药品", JButton.class).doClick();
            component(home, "home.category.其他", JButton.class).doClick();
        });

        assertThat(rendered).containsExactly(
                categorySearch("文具"), categorySearch("图书"), categorySearch("生活用品"),
                categorySearch("药品"), categorySearch("其他"));
    }

    @Test
    void cartPagePublishesLoadUpdateAndDeleteTotalsToTheSharedToolbar() throws Exception {
        CartMutationClient client = new CartMutationClient();
        CartCountModel count = new CartCountModel();
        ShopNavigator navigator = new ShopNavigator(route -> { });
        ShopToolbar toolbar = onEdt(() -> new ShopToolbar(
                navigator, count, new DefaultShopUiKit()));
        CartPanel cart = onEdt(() -> new CartPanel(client, navigator,
                new DefaultShopUiKit(), count, () -> { }));

        onEdt(cart::load);
        flushEdt();
        assertThat(component(toolbar, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（5）");
        onEdt(() -> cart.updateQuantity("cart-item-1", 4));
        flushEdt();
        assertThat(component(toolbar, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（7）");
        onEdt(() -> cart.remove("cart-item-1"));
        flushEdt();
        assertThat(component(toolbar, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（3）");
    }

    @Test
    void firstShopEntrySynchronizesThePersistedCartBeforeAnyCartPageVisit() throws Exception {
        CartMutationClient client = new CartMutationClient();
        ShopModulePanel content = onEdt(ShopModulePanel::new);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                content, buyer(), client, new DefaultShopUiKit(), () -> { }));

        onEdt(coordinator::enter);
        flushEdt();

        assertThat(component(content, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（5）");
        assertThat(coordinator.navigator().current()).contains(new ShopRoute.Home(defaultHome()));
    }

    @Test
    void laterShopEntryRetriesAnInitialCartCountSyncFailure() throws Exception {
        RetryingCartSyncClient client = new RetryingCartSyncClient();
        ShopModulePanel content = onEdt(ShopModulePanel::new);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                content, buyer(), client, new DefaultShopUiKit(), () -> { }));

        onEdt(coordinator::enter);
        flushEdt();
        assertThat(component(content, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（0）");
        onEdt(coordinator::enter);
        flushEdt();

        assertThat(client.cartLoads).isEqualTo(2);
        assertThat(component(content, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（5）");
    }

    @Test
    void olderCartLoadCannotOverwriteACompletedNewerAddToCartTotal() throws Exception {
        CartRaceClient client = new CartRaceClient();
        CartCountModel count = new CartCountModel();
        ShopNavigator navigator = new ShopNavigator(route -> { });
        ShopToolbar toolbar = onEdt(() -> new ShopToolbar(
                navigator, count, new DefaultShopUiKit()));
        CartPanel cart = onEdt(() -> new CartPanel(client, navigator,
                new DefaultShopUiKit(), count, () -> { }));
        ProductDetailPanel product = onEdt(() -> new ProductDetailPanel(client, navigator,
                new DefaultShopUiKit(), count, () -> { }));

        onEdt(cart::load);
        onEdt(() -> product.load("product-race"));
        flushEdt();
        onEdt(() -> component(product, "add-to-cart", JButton.class).doClick());
        client.add.complete(cartWithQuantities(2, 3));
        flushEdt();
        client.slowCart.complete(cartWithQuantities(1, 1));
        flushEdt();

        assertThat(component(toolbar, "shop.cart", JButton.class).getText())
                .isEqualTo("购物车（5）");
    }

    @Test
    void newHomeRequestInvalidatesAnAlreadyQueuedOldScrollRestore() throws Exception {
        ScrollRaceClient client = new ScrollRaceClient();
        ShopHomePanel home = onEdt(() -> new ShopHomePanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));
        JScrollPane scroll = component(home, "home.scroll", JScrollPane.class);
        HomeViewState oldState = new HomeViewState(defaultHome(), 360);
        HomeViewState newState = new HomeViewState(new HomeProductQuery(null, null,
                ProductSortMode.PRICE_DESC, 1, 20), 0);

        onEdt(() -> {
            scroll.getVerticalScrollBar().setValues(0, 10, 0, 1000);
            home.load(oldState);
        });
        client.first.complete(RestoringClient.productsPage());
        onEdt(() -> {
            home.load(newState);
            scroll.getVerticalScrollBar().setValues(17, 10, 0, 1000);
        });
        flushEdt();

        assertThat(scroll.getVerticalScrollBar().getValue()).isEqualTo(17);
    }

    @Test
    void backDepartureInvalidatesQueuedScrollRestoreForEveryListPage() throws Exception {
        assertEveryListRejectsQueuedRestore(ScrollDeparture.BACK);
    }

    @Test
    void resetDepartureInvalidatesQueuedScrollRestoreForEveryListPage() throws Exception {
        assertEveryListRejectsQueuedRestore(ScrollDeparture.RESET);
    }

    @Test
    void disposeInvalidatesQueuedScrollRestoreForEveryListPage() throws Exception {
        assertEveryListRejectsQueuedRestore(ScrollDeparture.DISPOSE);
    }
    @Test
    void rendersEachRouteByLoadingItsExactPayloadBeforeShowingItsCard() throws Exception {
        List<SequenceEvent> events = new ArrayList<>();
        SequencePageSet pages = onEdt(() -> new SequencePageSet(events));
        SequenceCards cards = new SequenceCards(events);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(cards, buyer(),
                (user, navigator, uiKit, homeExpired, searchExpired, productExpired,
                        storefrontExpired, cartExpired, checkoutExpired, myExpired) -> pages,
                new DefaultShopUiKit(), () -> { }));
        List<ShopRoute> routes = List.of(
                new ShopRoute.Home(defaultHome()),
                new ShopRoute.Search(defaultSearch()),
                new ShopRoute.Product("product-order"),
                new ShopRoute.Storefront("shop-order"),
                new ShopRoute.Cart(),
                new ShopRoute.Checkout(),
                new ShopRoute.PaymentResult(payment()),
                new ShopRoute.My());

        for (ShopRoute route : routes) {
            events.clear();
            onEdt(() -> coordinator.render(route));
            assertThat(events).containsExactly(
                    new SequenceEvent(operation(route),
                            new RouteInvocation(pageId(route), payload(route))),
                    new SequenceEvent("show", pageId(route)));
        }
    }

    @Test
    void disposeInvalidatesSevenPendingPagesBeforeAnyCompletionCanMutateUiOrSession() throws Exception {
        PendingClient client = new PendingClient();
        StateCountingKit uiKit = new StateCountingKit();
        AtomicInteger expired = new AtomicInteger();
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                new ShopModulePanel(), buyer(), client, uiKit, expired::incrementAndGet));

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(defaultHome())));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(defaultSearch())));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product")));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Storefront("shop")));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Cart()));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.My()));
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
                new ShopModulePanel(), buyer(), factory, new DefaultShopUiKit(), sessionExpired));

        assertThat(captures).extracting(CallbackCapture::page).containsExactly(
                "home", "search", "product", "storefront", "cart", "checkout", "my");
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
        ShopModulePanel content = onEdt(ShopModulePanel::new);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                content, buyer(), client, new DefaultShopUiKit(), () -> { }));
        assertThat(namedComponents(content, "shop.toolbar")).hasSize(1);
        JPanel cardHost = component(content, "shop.pages", JPanel.class);
        List<Component> fixedCards = Arrays.asList(cardHost.getComponents());
        assertThat(fixedCards).hasSize(11).extracting(Component::getName).containsExactly(
                "shop.home", "shop.search", "shop.product", "shop.storefront", "shop.cart",
                "shop.checkout", "shop.payment-result", "shop.my", "shop.seller-application",
                "shop.seller-workspace", "shop.admin-workspace");
        HomeProductQuery home = new HomeProductQuery(new BigDecimal("1.00"), new BigDecimal("9.00"),
                ProductSortMode.PRICE_DESC, 4, 8);
        ProductSearchQuery search = new ProductSearchQuery("本", "文具", new BigDecimal("1.00"),
                new BigDecimal("9.00"), ProductSortMode.SALES_DESC, 2, 6);
        PaymentView payment = payment();

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(home)));
        assertThat(client.homeQueries).containsExactly(home);
        assertVisible(content, "shop.home");
        assertFixedCards(cardHost, fixedCards);
        JPanel homePage = component(content, "shop.home", JPanel.class);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(search)));
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Search(search)));
        assertThat(client.searchQueries).containsExactly(search, search);
        assertThat(coordinator.navigator().history()).containsExactly(new ShopRoute.Home(home));
        assertVisible(content, "shop.search");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-7")));
        assertThat(client.productIds).containsExactly("product-7");
        assertVisible(content, "shop.product");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Storefront("shop-9")));
        assertThat(client.shopIds).containsExactly("shop-9");
        assertVisible(content, "shop.storefront");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Cart()));
        assertThat(client.cartLoads).isEqualTo(1);
        assertVisible(content, "shop.cart");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Checkout()));
        assertThat(client.cartLoads).isEqualTo(2);
        assertVisible(content, "shop.checkout");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.PaymentResult(payment)));
        assertVisible(content, "shop.payment-result");
        assertThat(component(content, "payment-number", JLabel.class).getText()).isEqualTo("P0007");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.My()));
        assertVisible(content, "shop.my");
        assertFixedCards(cardHost, fixedCards);

        onEdt(() -> coordinator.navigator().open(new ShopRoute.SellerApplication()));
        assertVisible(content, "shop.seller-application");
        onEdt(() -> coordinator.navigator().open(new ShopRoute.SellerWorkspace()));
        assertVisible(content, "shop.seller-workspace");
        onEdt(() -> coordinator.navigator().open(new ShopRoute.AdminWorkspace()));
        assertVisible(content, "shop.admin-workspace");
        assertFixedCards(cardHost, fixedCards);

        HomeProductQuery refreshedHome = new HomeProductQuery(null, new BigDecimal("12.00"),
                ProductSortMode.SALES_DESC, 1, 5);
        onEdt(() -> coordinator.navigator().open(new ShopRoute.Home(refreshedHome)));
        assertThat(client.homeQueries).containsExactly(home, refreshedHome);
        assertThat(component(content, "shop.home", JPanel.class)).isSameAs(homePage);
        assertVisible(content, "shop.home");
        assertFixedCards(cardHost, fixedCards);

        onEdt(coordinator::dispose);
        onEdt(coordinator::dispose);
        flushEdt();
    }

    @Test
    void renderingAnyPaymentReceiptLeavesTheCurrentToolbarCountUnchanged() throws Exception {
        BadgeClient client = new BadgeClient();
        ShopModulePanel content = onEdt(ShopModulePanel::new);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                content, buyer(), client, new DefaultShopUiKit(), () -> { }));

        onEdt(() -> coordinator.navigator().open(new ShopRoute.Product("product-badge")));
        flushEdt();
        onEdt(() -> component(content, "add-to-cart", JButton.class).doClick());
        flushEdt();
        assertThat(component(content, "shop.cart", JButton.class).getText()).isEqualTo("购物车（2）");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.PaymentResult(
                payment(PaymentStatus.CANCELLED))));
        assertThat(component(content, "shop.cart", JButton.class).getText()).isEqualTo("购物车（2）");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.PaymentResult(
                payment(PaymentStatus.PENDING))));
        assertThat(component(content, "shop.cart", JButton.class).getText()).isEqualTo("购物车（2）");

        onEdt(() -> coordinator.navigator().open(new ShopRoute.PaymentResult(payment())));

        assertThat(component(content, "shop.cart", JButton.class).getText()).isEqualTo("购物车（2）");

        onEdt(() -> coordinator.navigator().renderCurrent());
        assertThat(component(content, "shop.cart", JButton.class).getText()).isEqualTo("购物车（2）");
    }

    @Test
    void reusesTheSharedShopEntryAndLoadsHomeOnlyOnFirstEntry() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        RecordingClient client = new RecordingClient();
        NavigationCountingKit uiKit = new NavigationCountingKit();
        MainFrame frame = onEdt((Callable<MainFrame>) MainFrame::new);
        int navigationCount = frame.navigation().getComponentCount();

        onEdt(() -> ShopUiInstaller.install(frame, buyer(), client, uiKit, () -> { }));
        Container placeholder = component(frame.content(), "page.shop", Container.class);
        assertThat(Arrays.asList(placeholder.getComponents()))
                .singleElement()
                .isInstanceOf(ShopModulePanel.class);
        assertThat(namedComponents(placeholder, "page.breadcrumb")).isEmpty();
        assertThat(namedComponents(placeholder, "page.title")).isEmpty();
        assertThat(namedComponents(placeholder, "page.description")).isEmpty();
        assertThat(namedComponents(placeholder, "page.status")).isEmpty();
        AbstractButton shop = component(frame.navigation(), "navigation.shop", AbstractButton.class);
        onEdt(() -> shop.doClick());
        flushEdt();
        onEdt(() -> shop.doClick());
        flushEdt();

        assertThat(shop.getText()).isEqualTo("校园商城");
        assertThat(frame.navigation().getComponentCount()).isEqualTo(navigationCount);
        assertThat(namedComponents(frame.navigation(), "navigation.shop")).hasSize(1);
        assertThat(namedComponents(frame.navigation(), "shop.navigation")).isEmpty();
        assertThat(uiKit.navigationButtons).isEmpty();
        assertThat(client.homeQueries).containsExactly(new HomeProductQuery(null, null,
                ProductSortMode.SALES_DESC, 0, 20));
        assertVisible(frame.content(), "shop.home");
    }

    @Test
    void originalShopEntryCallsEnterExactlyOncePerClick() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        MainFrame frame = onEdt((Callable<MainFrame>) MainFrame::new);
        RecordingInstalledCoordinator coordinator = new RecordingInstalledCoordinator();

        onEdt(() -> ShopUiInstaller.install(frame, buyer(), new RecordingClient(),
                new DefaultShopUiKit(), () -> { },
                (module, user, client, uiKit, sessionExpired) -> coordinator));
        AbstractButton shop = component(frame.navigation(), "navigation.shop", AbstractButton.class);
        onEdt(() -> shop.doClick());

        assertThat(coordinator.entries).hasValue(1);
    }

    @Test
    void installerFailsFastWhenStableShopComponentsAreMissingOrAmbiguous() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        MainFrame missingEntry = onEdt((Callable<MainFrame>) MainFrame::new);
        onEdt(() -> component(missingEntry.navigation(), "navigation.shop", AbstractButton.class)
                .setName("navigation.other"));

        assertThatThrownBy(() -> onEdt(() -> ShopUiInstaller.install(missingEntry,
                buyer(), new RecordingClient(), new DefaultShopUiKit(), () -> { })))
                .hasCauseInstanceOf(IllegalStateException.class);

        MainFrame ambiguousPage = onEdt((Callable<MainFrame>) MainFrame::new);
        onEdt(() -> {
            JPanel duplicate = new JPanel();
            duplicate.setName("page.shop");
            ambiguousPage.content().add(duplicate);
        });

        assertThatThrownBy(() -> onEdt(() -> ShopUiInstaller.install(ambiguousPage,
                buyer(), new RecordingClient(), new DefaultShopUiKit(), () -> { })))
                .hasCauseInstanceOf(IllegalStateException.class);

        MainFrame failedCreation = onEdt((Callable<MainFrame>) MainFrame::new);
        Container untouched = component(failedCreation.content(), "page.shop", Container.class);
        List<Component> originalPlaceholderChildren = List.copyOf(
                Arrays.asList(untouched.getComponents()));
        assertThatThrownBy(() -> onEdt(() -> ShopUiInstaller.install(failedCreation,
                buyer(), new RecordingClient(), new DefaultShopUiKit(), () -> { },
                (module, user, client, uiKit, sessionExpired) -> {
                    throw new IllegalStateException("coordinator creation failed");
                }))).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(Arrays.asList(untouched.getComponents()))
                .containsExactlyElementsOf(originalPlaceholderChildren);
    }

    @Test
    void installerWindowClosingDisposesTheSameCoordinatorOnce() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        MainFrame frame = onEdt((Callable<MainFrame>) MainFrame::new);
        RecordingInstalledCoordinator coordinator = new RecordingInstalledCoordinator();
        onEdt(() -> frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE));

        onEdt(() -> ShopUiInstaller.install(frame, buyer(), new RecordingClient(),
                new DefaultShopUiKit(), () -> { },
                (module, user, client, uiKit, sessionExpired) -> coordinator));
        onEdt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        onEdt(() -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        flushEdt();

        assertThat(coordinator.disposals).hasValue(1);
        onEdt(frame::dispose);
    }

    @Test
    void installerProgrammaticWindowDisposeAlsoReleasesTheCoordinatorOnce() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        MainFrame frame = onEdt((Callable<MainFrame>) MainFrame::new);
        RecordingInstalledCoordinator coordinator = new RecordingInstalledCoordinator();

        onEdt(() -> ShopUiInstaller.install(frame, buyer(), new RecordingClient(),
                new DefaultShopUiKit(), () -> { },
                (module, user, client, uiKit, sessionExpired) -> coordinator));
        assertThat(frame.isDisplayable()).isFalse();
        onEdt(frame::addNotify);
        onEdt(frame::dispose);
        flushEdt();

        assertThat(coordinator.disposals).hasValue(1);
    }

    @Test
    void passesTheSameSessionExpiredCallbackToEveryAsynchronousFixedPage() throws Exception {
        ExpiringClient client = new ExpiringClient();
        AtomicInteger expired = new AtomicInteger();
        Runnable sessionExpired = expired::incrementAndGet;
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(
                new ShopModulePanel(), buyer(), client, new DefaultShopUiKit(), sessionExpired));

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
        onEdt(() -> coordinator.navigator().open(new ShopRoute.My()));
        client.paidOrders.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();

        assertThat(expired).hasValue(7);
        onEdt(coordinator::dispose);
    }

    @Test
    void laterEntryRestoresTheCurrentCardWithoutChangingShopHistory() throws Exception {
        List<SequenceEvent> events = new ArrayList<>();
        SequencePageSet pages = onEdt(() -> new SequencePageSet(events));
        SequenceCards cards = new SequenceCards(events);
        ShopPageCoordinator coordinator = onEdt(() -> new ShopPageCoordinator(cards, buyer(),
                (user, navigator, uiKit, homeExpired, searchExpired, productExpired,
                        storefrontExpired, cartExpired, checkoutExpired, myExpired) -> pages,
                new DefaultShopUiKit(), () -> { }));
        ShopRoute.Search search = new ShopRoute.Search(defaultSearch());

        onEdt(coordinator::enter);
        onEdt(() -> coordinator.navigator().open(search));
        events.clear();
        onEdt(coordinator::enter);

        assertThat(events).containsExactly(new SequenceEvent("show", "shop.search"));
        assertThat(coordinator.navigator().current()).contains(search);
        assertThat(coordinator.navigator().history()).containsExactly(new ShopRoute.Home(defaultHome()));
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

    private static UserView buyer() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 30, 0, 0);
        return new UserView("buyer-7", "DEMO_BUYER", UserRole.STUDENT,
                AccountStatus.ACTIVE, false, now, 0, now, now);
    }

    private static PaidOrderHistory paidOrders() {
        PaidOrderView newest = new PaidOrderView(
                "order-new", "O-NEW", "shop-1", "校园文具店",
                new BigDecimal("6"), Instant.parse("2026-08-30T02:00:00Z"),
                OrderStatus.PAID, List.of(new PaidOrderItemView(
                        "product-new", "签字笔", "sku-new", "黑色", 2,
                        new BigDecimal("3"), new BigDecimal("6"))));
        PaidOrderView older = new PaidOrderView(
                "order-old", "O-OLD", "shop-2", "校园书店",
                new BigDecimal("12"), Instant.parse("2026-08-29T02:00:00Z"),
                OrderStatus.PAID, List.of(new PaidOrderItemView(
                        "product-old", "习题册", "sku-old", "高数", 1,
                        new BigDecimal("12"), new BigDecimal("12"))));
        return new PaidOrderHistory(List.of(newest, older));
    }

    private static PaidOrderHistory scrollingPaidOrders() {
        return new PaidOrderHistory(IntStream.range(0, 6).mapToObj(orderIndex -> {
            int itemCount = orderIndex == 5 ? 8 : 2;
            List<PaidOrderItemView> items = IntStream.range(0, itemCount)
                    .mapToObj(itemIndex -> new PaidOrderItemView(
                            "product-scroll-" + orderIndex + "-" + itemIndex,
                            "商品" + itemIndex,
                            "sku-scroll-" + orderIndex + "-" + itemIndex,
                            "规格" + itemIndex,
                            1, new BigDecimal("2.00"), new BigDecimal("2.00")))
                    .toList();
            return new PaidOrderView(
                    "order-scroll-" + orderIndex,
                    "O-SCROLL-" + orderIndex,
                    "shop-scroll-" + orderIndex,
                    "店铺" + orderIndex,
                    new BigDecimal(itemCount == 8 ? "16.00" : "4.00"),
                    Instant.parse("2026-08-30T02:00:00Z").minusSeconds(orderIndex * 60L),
                    OrderStatus.PAID, items);
        }).toList());
    }

    private static void layoutAt(Container root, int width, int height) {
        root.setSize(width, height);
        for (int pass = 0; pass < 3; pass++) {
            layoutRecursively(root);
        }
    }

    private static void layoutRecursively(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                layoutRecursively(nested);
            }
        }
    }

    @SafeVarargs
    private static ShopClientPort paidOrdersClient(
            CompletableFuture<PaidOrderHistory>... responses) {
        return new RecordingClient() {
            private int request;

            @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() {
                return responses[request++];
            }
        };
    }

    private static void assertEveryListRejectsQueuedRestore(ScrollDeparture departure)
            throws Exception {
        for (ListPage page : ListPage.values()) {
            ScrollDepartureFixture fixture = new ScrollDepartureFixture(page);
            fixture.start(departure);
            fixture.completeThenDepart(departure);
            assertThat(fixture.scroll.getVerticalScrollBar().getValue())
                    .as("%s via %s", page, departure)
                    .isEqualTo(17);
        }
    }

    private static PaymentView payment(PaymentStatus status) {
        return new PaymentView("payment-7", "group-7", "P0007", new BigDecimal("7.00"),
                status, status == PaymentStatus.SUCCEEDED ? PaymentChannel.WECHAT : null,
                Instant.parse("2026-08-30T00:00:00Z"),
                status == PaymentStatus.PENDING ? null : Instant.parse("2026-08-30T00:01:00Z"), 0);
    }

    private static CartView cartWithQuantities(int first, int second) {
        return new CartView("cart-1", List.of(
                new edu.seu.vcampus.common.shop.CartItemView("cart-item-1", "product-1", "签字笔",
                        "sku-1", "黑色", "shop-1", "校园文具店",
                        new BigDecimal("3.00"), first, 0),
                new edu.seu.vcampus.common.shop.CartItemView("cart-item-2", "product-2", "练习本",
                        "sku-2", "A5", "shop-1", "校园文具店",
                        new BigDecimal("4.00"), second, 0)),
                new BigDecimal("10.00"));
    }

    private static HomeProductQuery defaultHome() {
        return new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20);
    }

    private static ProductSearchQuery defaultSearch() {
        return new ProductSearchQuery(null, null, null, null, ProductSortMode.SALES_DESC, 0, 20);
    }

    private static ShopRoute.Search categorySearch(String category) {
        return new ShopRoute.Search(new SearchViewState(new ProductSearchQuery(null, category,
                null, null, ProductSortMode.SALES_DESC, 0, 20), false, false, 0));
    }

    private static ShopClientPort homeClient(PageResult<ProductSummary> homeResult) {
        return new ShopClientPort() {
            @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
                return CompletableFuture.completedFuture(homeResult);
            }
            @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<ShopDetail> getShop(String shopId) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(
                    ShopProductQuery query) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
            @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
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
        };
    }

    private static ShopClientPort searchClient(
            CompletableFuture<PageResult<ProductSummary>> searchResult) {
        return new ShopClientPort() {
            @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) {
                return searchResult;
            }
            @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<ShopDetail> getShop(String shopId) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(
                    ShopProductQuery query) {
                return new CompletableFuture<>();
            }
            @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
            @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
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
        };
    }

    private static String pageId(ShopRoute route) {
        return switch (route) {
            case ShopRoute.Home ignored -> "shop.home";
            case ShopRoute.Search ignored -> "shop.search";
            case ShopRoute.Product ignored -> "shop.product";
            case ShopRoute.Storefront ignored -> "shop.storefront";
            case ShopRoute.Cart ignored -> "shop.cart";
            case ShopRoute.Checkout ignored -> "shop.checkout";
            case ShopRoute.PaymentResult ignored -> "shop.payment-result";
            case ShopRoute.My ignored -> "shop.my";
            case ShopRoute.SellerApplication ignored -> "shop.seller-application";
            case ShopRoute.SellerWorkspace ignored -> "shop.seller-workspace";
            case ShopRoute.AdminWorkspace ignored -> "shop.admin-workspace";
        };
    }

    private static String operation(ShopRoute route) {
        return route instanceof ShopRoute.Search ? "search" : "load";
    }

    private static Object payload(ShopRoute route) {
        return switch (route) {
            case ShopRoute.Home(var state) -> state;
            case ShopRoute.Search(var state) -> state;
            case ShopRoute.Product(var productId) -> productId;
            case ShopRoute.Storefront(var state) -> state;
            case ShopRoute.Cart ignored -> null;
            case ShopRoute.Checkout ignored -> null;
            case ShopRoute.PaymentResult(var payment) -> payment;
            case ShopRoute.My ignored -> null;
            case ShopRoute.SellerApplication ignored -> null;
            case ShopRoute.SellerWorkspace ignored -> null;
            case ShopRoute.AdminWorkspace ignored -> null;
        };
    }

    private static class RecordingClient implements ShopClientPort {
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
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
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

    private static final class RestoringClient implements ShopClientPort {
        private final List<HomeProductQuery> homeQueries = new ArrayList<>();
        private final List<ProductSearchQuery> searchQueries = new ArrayList<>();
        private final List<ShopProductQuery> shopQueries = new ArrayList<>();

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
            homeQueries.add(query);
            return CompletableFuture.completedFuture(productsPage());
        }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) {
            searchQueries.add(query);
            return CompletableFuture.completedFuture(productsPage());
        }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
            return CompletableFuture.completedFuture(ShopClientFixtures.productDetail());
        }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) {
            return CompletableFuture.completedFuture(ShopClientFixtures.shopDetail());
        }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(
                ShopProductQuery query) {
            shopQueries.add(query);
            return CompletableFuture.completedFuture(productsPage());
        }
        @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }

        private static PageResult<ProductSummary> productsPage() {
            return new PageResult<>(IntStream.range(0, 30)
                    .mapToObj(index -> ShopClientFixtures.productSummary()).toList(), 0, 30, 30);
        }
    }

    private static final class CartMutationClient implements ShopClientPort {
        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() {
            return CompletableFuture.completedFuture(cartWithQuantities(2, 3));
        }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) {
            return CompletableFuture.completedFuture(cartWithQuantities(4, 3));
        }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) {
            var second = cartWithQuantities(2, 3).items().get(1);
            return CompletableFuture.completedFuture(new CartView("cart-1", List.of(second),
                    new BigDecimal("12.00")));
        }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }
    }

    private static final class CartRaceClient implements ShopClientPort {
        private final CompletableFuture<CartView> slowCart = new CompletableFuture<>();
        private final CompletableFuture<CartView> add = new CompletableFuture<>();

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
            return CompletableFuture.completedFuture(new ProductDetail(productId, "签字笔", "文具", "",
                    ProductStatus.ACTIVE, 0, new ShopSummary("shop-race", "校园文具店"), List.of(
                    new ProductSkuView("sku-race", "黑色", new BigDecimal("3.00"), 10, true, 0)),
                    Instant.parse("2026-08-30T00:00:00Z")));
        }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return slowCart; }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return add; }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }
    }

    private static final class RetryingCartSyncClient extends RecordingClient {
        private int cartLoads;

        @Override public CompletableFuture<CartView> getCart() {
            cartLoads++;
            if (cartLoads == 1) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("SHOP_UNAVAILABLE"));
            }
            return CompletableFuture.completedFuture(cartWithQuantities(2, 3));
        }
    }

    private static final class PaidOrdersCountingClient extends RecordingClient {
        private int paidOrderLoads;

        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() {
            paidOrderLoads++;
            return CompletableFuture.completedFuture(paidOrders());
        }
    }

    private static final class ScrollRaceClient implements ShopClientPort {
        private final CompletableFuture<PageResult<ProductSummary>> first = new CompletableFuture<>();
        private final CompletableFuture<PageResult<ProductSummary>> second = new CompletableFuture<>();
        private int homeCalls;

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
            return homeCalls++ == 0 ? first : second;
        }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }
    }

    private enum ListPage { HOME, SEARCH, STOREFRONT }
    private enum ScrollDeparture { BACK, RESET, DISPOSE }

    private static final class ScrollDepartureFixture {
        private final DepartureScrollClient client = new DepartureScrollClient();
        private final AtomicReference<Consumer<ShopRoute>> renderer =
                new AtomicReference<>(route -> { });
        private final ShopNavigator navigator = new ShopNavigator(
                route -> renderer.get().accept(route));
        private final JPanel panel;
        private final JScrollPane scroll;
        private final ShopRoute listRoute;
        private final Runnable dispose;

        private ScrollDepartureFixture(ListPage page) throws Exception {
            switch (page) {
                case HOME -> {
                    ShopHomePanel home = onEdt(() -> new ShopHomePanel(client, navigator,
                            new DefaultShopUiKit(), () -> { }));
                    HomeViewState state = new HomeViewState(defaultHome(), 360);
                    panel = home;
                    listRoute = new ShopRoute.Home(state);
                    dispose = home::dispose;
                    renderer.set(route -> {
                        if (route instanceof ShopRoute.Home(var requested)) home.load(requested);
                    });
                }
                case SEARCH -> {
                    ProductSearchPanel search = onEdt(() -> new ProductSearchPanel(client,
                            navigator, new DefaultShopUiKit(), () -> { }));
                    SearchViewState state = new SearchViewState(defaultSearch(), true, 360);
                    panel = search;
                    listRoute = new ShopRoute.Search(state);
                    dispose = search::dispose;
                    renderer.set(route -> {
                        if (route instanceof ShopRoute.Search(var requested)) search.search(requested);
                    });
                }
                case STOREFRONT -> {
                    BuyerShopPanel storefront = onEdt(() -> new BuyerShopPanel(client,
                            navigator, new DefaultShopUiKit(), () -> { }));
                    StorefrontViewState state = new StorefrontViewState(new ShopProductQuery(
                            "shop-1", null, null, null, null,
                            ProductSortMode.SALES_DESC, 0, 20), 360);
                    panel = storefront;
                    listRoute = new ShopRoute.Storefront(state);
                    dispose = storefront::dispose;
                    renderer.set(route -> {
                        if (route instanceof ShopRoute.Storefront(var requested)) {
                            storefront.load(requested);
                        }
                    });
                }
                default -> throw new IllegalStateException("Unknown list page");
            }
            scroll = component(panel, switch (page) {
                case HOME -> "home.scroll";
                case SEARCH -> "search.scroll";
                case STOREFRONT -> "storefront.scroll";
            }, JScrollPane.class);
        }

        private void start(ScrollDeparture departure) throws Exception {
            onEdt(() -> {
                scroll.getVerticalScrollBar().setValues(0, 10, 0, 1000);
                if (departure == ScrollDeparture.BACK) {
                    navigator.reset(new ShopRoute.My());
                    navigator.open(listRoute);
                } else {
                    navigator.reset(listRoute);
                }
            });
            flushEdt();
        }

        private void completeThenDepart(ScrollDeparture departure) throws Exception {
            client.result.complete(RestoringClient.productsPage());
            onEdt(() -> {
                switch (departure) {
                    case BACK -> navigator.back();
                    case RESET -> navigator.reset(new ShopRoute.My());
                    case DISPOSE -> dispose.run();
                }
                scroll.getVerticalScrollBar().setValues(17, 10, 0, 1000);
            });
            flushEdt();
        }
    }

    private static final class DepartureScrollClient implements ShopClientPort {
        private final CompletableFuture<PageResult<ProductSummary>> result =
                new CompletableFuture<>();

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return result; }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return result; }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) {
            return CompletableFuture.completedFuture(ShopClientFixtures.shopDetail());
        }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return result; }
        @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }
    }

    private static List<Component> namedComponents(java.awt.Container root, String name) {
        List<Component> matches = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) {
                matches.add(child);
            }
            if (child instanceof java.awt.Container nested) {
                matches.addAll(namedComponents(nested, name));
            }
        }
        return matches;
    }

    private static final class BadgeClient implements ShopClientPort {
        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
            return CompletableFuture.completedFuture(new ProductDetail(productId, "签字笔", "文具", "",
                    ProductStatus.ACTIVE, 0, new ShopSummary("shop-badge", "校园文具店"), List.of(
                    new ProductSkuView("sku-badge", "黑色", new BigDecimal("3.00"), 10, true, 0)),
                    Instant.parse("2026-08-30T00:00:00Z")));
        }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> addToCart(AddCartItemCommand command) {
            return CompletableFuture.completedFuture(ShopClientFixtures.cartView());
        }
        @Override public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> removeCartItem(String cartItemId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) { return new CompletableFuture<>(); }
    }

    private static final class ExpiringClient implements ShopClientPort {
        private final CompletableFuture<PageResult<ProductSummary>> home = new CompletableFuture<>();
        private final CompletableFuture<PageResult<ProductSummary>> search = new CompletableFuture<>();
        private final CompletableFuture<ProductDetail> product = new CompletableFuture<>();
        private final CompletableFuture<ShopDetail> storefront = new CompletableFuture<>();
        private final CompletableFuture<CartView> cart = new CompletableFuture<>();
        private final CompletableFuture<CartView> checkoutCart = new CompletableFuture<>();
        private final CompletableFuture<PaidOrderHistory> paidOrders = new CompletableFuture<>();

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return home; }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return search; }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return product; }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return storefront; }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() {
            return cart.isDone() ? checkoutCart : cart;
        }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return paidOrders; }
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
        private final CompletableFuture<PaidOrderHistory> paidOrders = new CompletableFuture<>();
        private int nextCart;

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return home; }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return search; }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return product; }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return storefront; }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return carts.get(nextCart++); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return paidOrders; }
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
            paidOrders.completeExceptionally(expired);
        }
    }

    private static final class StateCountingKit implements ShopUiKit {
        private final ShopUiKit delegate = new DefaultShopUiKit();
        private final AtomicInteger stateCalls = new AtomicInteger();
        private final List<ShopPageState> states = new ArrayList<>();

        @Override public JButton navigationButton(String name, String text) { return delegate.navigationButton(name, text); }
        @Override public JButton primaryButton(String name, String text) { return delegate.primaryButton(name, text); }
        @Override public JButton secondaryButton(String name, String text) { return delegate.secondaryButton(name, text); }
        @Override public JPanel filterPanel(String name, LayoutManager layout) { return delegate.filterPanel(name, layout); }
        @Override public JPanel productCard(String name, LayoutManager layout) { return delegate.productCard(name, layout); }
        @Override public JComponent stateView(String name, ShopPageState state, String message, Runnable retry) {
            stateCalls.incrementAndGet();
            states.add(state);
            return delegate.stateView(name, state, message, retry);
        }
    }

    private static final class RecordingInstalledCoordinator implements ShopUiInstaller.InstalledCoordinator {
        private final AtomicInteger disposals = new AtomicInteger();
        private final AtomicInteger entries = new AtomicInteger();
        private final edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator navigator =
                new edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator(route -> { });

        @Override public edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator navigator() { return navigator; }
        @Override public void enter() { entries.incrementAndGet(); }
        @Override public void dispose() { disposals.incrementAndGet(); }
    }

    private record CallbackCapture(String page, Runnable callback) { }
    private record RouteInvocation(String pageId, Object payload) { }
    private record SequenceEvent(String operation, Object value) { }

    private static final class SequenceCards implements ShopPageCoordinator.CardNavigator {
        private final List<SequenceEvent> events;

        private SequenceCards(List<SequenceEvent> events) { this.events = events; }
        @Override public void register(String pageId, JPanel page) { }
        @Override public void show(String pageId) { events.add(new SequenceEvent("show", pageId)); }
    }

    private static final class SequencePageSet implements ShopPageCoordinator.PageSet {
        private final List<SequenceEvent> events;
        private final JPanel home = new JPanel();
        private final JPanel search = new JPanel();
        private final JPanel product = new JPanel();
        private final JPanel storefront = new JPanel();
        private final JPanel cart = new JPanel();
        private final JPanel checkout = new JPanel();
        private final JPanel paymentResult = new JPanel();
        private final JPanel my = new JPanel();

        private SequencePageSet(List<SequenceEvent> events) { this.events = events; }
        @Override public JPanel home() { return home; }
        @Override public JPanel search() { return search; }
        @Override public JPanel product() { return product; }
        @Override public JPanel storefront() { return storefront; }
        @Override public JPanel cart() { return cart; }
        @Override public JPanel checkout() { return checkout; }
        @Override public JPanel paymentResult() { return paymentResult; }
        @Override public JPanel my() { return my; }
        @Override public void loadHome(HomeViewState state) { load("shop.home", state); }
        @Override public void search(SearchViewState state) {
            events.add(new SequenceEvent("search", new RouteInvocation("shop.search", state)));
        }
        @Override public void loadProduct(String productId) { load("shop.product", productId); }
        @Override public void loadStorefront(StorefrontViewState state) { load("shop.storefront", state); }
        @Override public void loadCart() { load("shop.cart", null); }
        @Override public void loadCheckout() { load("shop.checkout", null); }
        @Override public void loadPaymentResult(PaymentView payment) { load("shop.payment-result", payment); }
        @Override public void loadMy() { load("shop.my", null); }
        @Override public HomeViewState captureHome(HomeViewState state) { return state; }
        @Override public SearchViewState captureSearch(SearchViewState state) { return state; }
        @Override public StorefrontViewState captureStorefront(StorefrontViewState state) { return state; }
        @Override public void dispose() { }

        private void load(String pageId, Object payload) {
            events.add(new SequenceEvent("load", new RouteInvocation(pageId, payload)));
        }
    }

    private static final class CheckoutSuccessClient implements ShopClientPort {
        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ProductDetail> getProduct(String productId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<ShopDetail> getShop(String shopId) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) { return new CompletableFuture<>(); }
        @Override public CompletableFuture<CartView> getCart() { return CompletableFuture.completedFuture(ShopClientFixtures.cartView()); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() { return new CompletableFuture<>(); }
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
