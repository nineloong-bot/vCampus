package edu.seu.vcampus.client.shop.ui.style;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.ProductSearchPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ProductDetailPanel;
import edu.seu.vcampus.client.shop.ui.buyer.BuyerShopPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ShopHomePanel;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSkuView;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ShopSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopStatus;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopUiKitTest {
    @Test
    void buyerSourcesDoNotOwnThemeStyling() throws Exception {
        String buyerSources;
        try (Stream<Path> files = Files.walk(Path.of(
                "src/main/java/edu/seu/vcampus/client/shop/ui/buyer"))) {
            buyerSources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> assertDoesNotThrow(() -> Files.readString(path)))
                    .collect(Collectors.joining("\n"));
        }

        assertThat(buyerSources).doesNotContain("java.awt.Color", "new Font", "BorderFactory");
    }

    @Test
    void homeAndResultsDelegateButtonsCardsAndStatesToInjectedKit() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.home(any())).thenReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(
                new ProductSummary("product-1", "shop-1", "文具店", "签字笔", "文具",
                        new BigDecimal("3.00"), 2, Instant.EPOCH)), 0, 20, 1)));
        RecordingKit kit = new RecordingKit();
        ShopHomePanel panel = onEdt(() -> new ShopHomePanel(client,
                new ShopNavigator(route -> { }), kit, () -> { }));

        onEdt(() -> panel.load());
        flushEdt();

        assertThat(kit.primaryButtons).contains("home.search");
        assertThat(kit.productCards).contains("product-product-1");
        assertThat(kit.states).contains(ShopPageState.LOADING, ShopPageState.NORMAL);
        assertThat(component(panel, "home.state", JLabel.class).getText()).isEmpty();
    }

    @Test
    void searchReportsEmptyErrorAndDisconnectedStatesThroughInjectedKit() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PageResult<ProductSummary>> empty = CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 20, 0));
        CompletableFuture<PageResult<ProductSummary>> failed = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> expired = new CompletableFuture<>();
        when(client.search(any())).thenReturn(empty, failed, expired);
        RecordingKit kit = new RecordingKit();
        List<String> expiredSignals = new ArrayList<>();
        ProductSearchPanel panel = onEdt(() -> new ProductSearchPanel(client,
                new ShopNavigator(route -> { }), kit, () -> expiredSignals.add("expired")));
        ProductSearchQuery query = new ProductSearchQuery(null, null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);

        onEdt(() -> panel.search(query));
        flushEdt();
        onEdt(() -> panel.search(query));
        failed.completeExceptionally(new IllegalStateException("SHOP_UNAVAILABLE"));
        flushEdt();
        onEdt(() -> panel.search(query));
        expired.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();

        assertThat(kit.states).contains(ShopPageState.LOADING, ShopPageState.EMPTY,
                ShopPageState.ERROR, ShopPageState.DISCONNECTED);
        assertThat(expiredSignals).containsExactly("expired");
        assertThat(component(panel, "search.state", JLabel.class).getText())
                .isEqualTo("AUTH_SESSION_EXPIRED");
    }

    @Test
    void workerCompletionInvokesKitAndPublishesItsViewsOnEdt() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PageResult<ProductSummary>> result = new CompletableFuture<>();
        when(client.home(any())).thenReturn(result);
        RecordingKit kit = new RecordingKit();
        ShopHomePanel panel = onEdt(() -> new ShopHomePanel(client,
                new ShopNavigator(route -> { }), kit, () -> { }));

        onEdt(() -> panel.load());
        Thread worker = new Thread(() -> result.complete(new PageResult<>(List.of(
                new ProductSummary("worker", "shop", "店", "工作线程商品", "文具",
                        new BigDecimal("3.00"), 0, Instant.EPOCH)), 0, 20, 1)));
        worker.start();
        worker.join();
        flushEdt();

        assertThat(kit.uiThreadCalls).allMatch(Boolean::booleanValue);
        assertThat(component(panel, "home.state", JLabel.class).getText()).isEmpty();
    }

    @Test
    void detailAndStorefrontMountInjectedKitStateViewsForReachableLifecycles() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<ProductDetail> detail = new CompletableFuture<>();
        CompletableFuture<edu.seu.vcampus.common.shop.CartView> add = new CompletableFuture<>();
        CompletableFuture<ShopDetail> shop = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> products = new CompletableFuture<>();
        when(client.getProduct("detail")).thenReturn(detail);
        when(client.addToCart(any())).thenReturn(add);
        when(client.getShop("store")).thenReturn(shop);
        when(client.getShopProducts(any())).thenReturn(products);
        RecordingKit kit = new RecordingKit();
        ProductDetailPanel detailPanel = onEdt(() -> new ProductDetailPanel(client,
                new ShopNavigator(route -> { }), kit, () -> { }));
        BuyerShopPanel storePanel = onEdt(() -> new BuyerShopPanel(client,
                new ShopNavigator(route -> { }), kit, () -> { }));

        assertThat(component(detailPanel, "detail.state", JLabel.class)).isNotNull();
        assertThat(component(storePanel, "storefront.state", JLabel.class)).isNotNull();
        onEdt(() -> { detailPanel.load("detail"); storePanel.load("store"); });
        detail.complete(new ProductDetail("detail", "详情", "文具", "", ProductStatus.ACTIVE, 0,
                new ShopSummary("shop", "店"), List.of(new ProductSkuView("sku", "规格",
                new BigDecimal("3.00"), 2, true, 0)), Instant.EPOCH));
        shop.complete(new ShopDetail("store", "店铺", "", "文具", "", ShopStatus.ACTIVE));
        flushEdt();
        flushEdt();
        onEdt(() -> component(detailPanel, "add-to-cart", JButton.class).doClick());
        assertThat(component(detailPanel, "detail.state", JLabel.class).getText()).contains("加入");
        add.completeExceptionally(new IllegalStateException("SHOP_UNAVAILABLE"));
        products.complete(new PageResult<>(List.of(), 0, 20, 0));
        flushEdt();
        flushEdt();

        assertThat(kit.states).contains(ShopPageState.INITIAL, ShopPageState.LOADING,
                ShopPageState.NORMAL, ShopPageState.SUBMITTING, ShopPageState.ERROR,
                ShopPageState.EMPTY);
        assertThat(component(detailPanel, "detail.state", JLabel.class).getText())
                .isEqualTo("SHOP_UNAVAILABLE");
        assertThat(component(storePanel, "storefront.state", JLabel.class).getText())
                .isEqualTo("暂无商品");
    }

    private static final class RecordingKit implements ShopUiKit {
        private final List<String> primaryButtons = new ArrayList<>();
        private final List<String> productCards = new ArrayList<>();
        private final EnumSet<ShopPageState> states = EnumSet.noneOf(ShopPageState.class);
        private final List<Boolean> uiThreadCalls = new ArrayList<>();

        @Override
        public JButton primaryButton(String name, String text) {
            primaryButtons.add(name);
            return named(new JButton(text), name);
        }

        @Override
        public JButton secondaryButton(String name, String text) {
            return named(new JButton(text), name);
        }

        @Override
        public JPanel filterPanel(String name, LayoutManager layout) {
            return named(new JPanel(layout), name);
        }

        @Override
        public JPanel productCard(String name, LayoutManager layout) {
            uiThreadCalls.add(javax.swing.SwingUtilities.isEventDispatchThread());
            productCards.add(name);
            return named(new JPanel(layout), name);
        }

        @Override
        public JComponent stateView(String name, ShopPageState state, String message,
                Runnable retry) {
            uiThreadCalls.add(javax.swing.SwingUtilities.isEventDispatchThread());
            states.add(state);
            return named(new JLabel(message), name);
        }

        private static <T extends JComponent> T named(T component, String name) {
            component.setName(name);
            return component;
        }
    }
}
