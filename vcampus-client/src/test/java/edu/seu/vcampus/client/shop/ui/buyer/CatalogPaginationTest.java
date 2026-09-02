package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.navigation.HomeViewState;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRouteHost;
import edu.seu.vcampus.client.shop.ui.navigation.StorefrontViewState;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogPaginationTest {
    @Test
    void homeButtonsReplaceThePageAndDetailBackRestoresPageAndScroll() throws Exception {
        PagingClient client = new PagingClient();
        AtomicReference<ShopHomePanel> panelRef = new AtomicReference<>();
        ShopNavigator navigator = new ShopNavigator(new ShopRouteHost() {
            @Override public ShopRoute capture(ShopRoute route) {
                if (route instanceof ShopRoute.Home(var state)) {
                    return new ShopRoute.Home(panelRef.get().capture(state));
                }
                return route;
            }

            @Override public void render(ShopRoute route) {
                if (route instanceof ShopRoute.Home(var state)) {
                    panelRef.get().load(state);
                }
            }
        });
        ShopHomePanel panel = onEdt(() -> new ShopHomePanel(
                client, navigator, new DefaultShopUiKit(), () -> { }));
        panelRef.set(panel);
        HomeProductQuery first = new HomeProductQuery(
                new BigDecimal("1.00"), new BigDecimal("99.00"),
                ProductSortMode.SALES_DESC, 0, 1);

        onEdt(() -> navigator.reset(new ShopRoute.Home(first)));
        flushUi();
        assertThat(component(panel, "home.keyword", JTextField.class)
                .getClientProperty("shop.placeholderText"))
                .isEqualTo("搜索商品、店铺或相关信息……");
        JButton previous = component(panel, "home.pagination.previous", JButton.class);
        JButton next = component(panel, "home.pagination.next", JButton.class);
        assertThat(previous.getText()).isEqualTo("上一页");
        assertThat(next.getText()).isEqualTo("下一页");
        assertThat(previous.isEnabled()).isFalse();
        assertThat(next.isEnabled()).isTrue();
        assertThat(component(panel, "home.pagination.status", JLabel.class).getText())
                .isEqualTo("第 1 / 3 页");

        onEdt((Runnable) next::doClick);
        flushUi();
        HomeProductQuery second = new HomeProductQuery(
                first.minPrice(), first.maxPrice(), first.sortMode(), 1, first.pageSize());
        assertThat(client.homeQueries).containsExactly(first, second);
        assertThat(navigator.current()).contains(new ShopRoute.Home(
                new HomeViewState(second, 0)));
        assertThat(navigator.history()).isEmpty();

        JScrollPane scroll = component(panel, "home.scroll", JScrollPane.class);
        onEdt(() -> scroll.getVerticalScrollBar().setValues(120, 10, 0, 1000));
        onEdt(() -> clickCard(component(panel, "product-product-1", JPanel.class)));
        assertThat(navigator.current()).contains(new ShopRoute.Product("product-1"));
        onEdt((Runnable) navigator::back);
        flushUi();

        assertThat(client.homeQueries).containsExactly(first, second, second);
        assertThat(navigator.current()).contains(new ShopRoute.Home(
                new HomeViewState(second, 120)));
        assertThat(scroll.getVerticalScrollBar().getValue()).isEqualTo(120);
        assertThat(navigator.history()).isEmpty();
    }

    private static void clickCard(JPanel card) {
        java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(card,
                java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false);
        for (java.awt.event.MouseListener listener : card.getMouseListeners()) listener.mouseClicked(event);
    }

    @Test
    void searchButtonsPreserveEveryFilterUseReplaceAndDisableAtBoundaries() throws Exception {
        PagingClient client = new PagingClient();
        AtomicReference<ProductSearchPanel> panelRef = new AtomicReference<>();
        ShopNavigator navigator = new ShopNavigator(new ShopRouteHost() {
            @Override public ShopRoute capture(ShopRoute route) {
                if (route instanceof ShopRoute.Search(var state)) {
                    return new ShopRoute.Search(panelRef.get().capture(state));
                }
                return route;
            }

            @Override public void render(ShopRoute route) {
                if (route instanceof ShopRoute.Search(var state)) {
                    panelRef.get().search(state);
                }
            }
        });
        ProductSearchPanel panel = onEdt(() -> new ProductSearchPanel(
                client, navigator, new DefaultShopUiKit(), () -> { }));
        panelRef.set(panel);
        ProductSearchQuery first = new ProductSearchQuery(
                "雨伞", "生活用品", new BigDecimal("10.00"), new BigDecimal("80.00"),
                ProductSortMode.PRICE_DESC, 0, 1);

        onEdt(() -> navigator.openFromRoot(ShopRoute.defaultHome(), new ShopRoute.Search(
                new SearchViewState(first, true, true, 0))));
        flushUi();
        assertThat(component(panel, "keyword", JTextField.class)
                .getClientProperty("shop.placeholderText"))
                .isEqualTo("搜索商品、店铺或相关信息……");
        assertThat(component(panel, "search.category.label", JLabel.class).getText())
                .isEqualTo("分类");
        assertThat(component(panel, "search.min-price.label", JLabel.class).getText())
                .isEqualTo("最低价");
        assertThat(component(panel, "search.max-price.label", JLabel.class).getText())
                .isEqualTo("最高价");
        assertThat(component(panel, "search.sort.label", JLabel.class).getText())
                .isEqualTo("排序方式");
        JButton previous = component(panel, "search.pagination.previous", JButton.class);
        JButton next = component(panel, "search.pagination.next", JButton.class);
        assertThat(previous.isEnabled()).isFalse();
        assertThat(next.isEnabled()).isTrue();

        onEdt((Runnable) next::doClick);
        flushUi();
        ProductSearchQuery second = new ProductSearchQuery(
                first.keyword(), first.category(), first.minPrice(), first.maxPrice(),
                first.sortMode(), 1, first.pageSize());
        assertThat(client.searchQueries).containsExactly(first, second);
        assertThat(navigator.current()).contains(new ShopRoute.Search(
                new SearchViewState(second, true, true, 0)));
        assertThat(navigator.history()).containsExactly(ShopRoute.defaultHome());

        onEdt((Runnable) next::doClick);
        flushUi();
        assertThat(component(panel, "search.pagination.next", JButton.class).isEnabled())
                .isFalse();
        assertThat(component(panel, "search.pagination.previous", JButton.class).isEnabled())
                .isTrue();
        assertThat(component(panel, "search.pagination.status", JLabel.class).getText())
                .isEqualTo("第 3 / 3 页");

        JScrollPane scroll = component(panel, "search.scroll", JScrollPane.class);
        onEdt(() -> scroll.getVerticalScrollBar().setValues(90, 10, 0, 1000));
        assertThat(scroll.getVerticalScrollBar().getValue()).isPositive();
        onEdt(() -> {
            component(panel, "keyword", JTextField.class).setText("教材");
            component(panel, "search", JButton.class).doClick();
        });
        flushUi();
        ProductSearchQuery submitted = new ProductSearchQuery(
                "教材", first.category(), first.minPrice(), first.maxPrice(),
                first.sortMode(), 0, first.pageSize());
        assertThat(client.searchQueries.getLast()).isEqualTo(submitted);
        assertThat(navigator.current()).contains(new ShopRoute.Search(
                new SearchViewState(submitted, true, true, 0)));
        assertThat(navigator.history()).containsExactly(ShopRoute.defaultHome());
    }

    @Test
    void storefrontButtonsPreserveTheShopQueryAndResetScroll() throws Exception {
        PagingClient client = new PagingClient();
        AtomicReference<BuyerShopPanel> panelRef = new AtomicReference<>();
        ShopNavigator navigator = new ShopNavigator(new ShopRouteHost() {
            @Override public ShopRoute capture(ShopRoute route) {
                if (route instanceof ShopRoute.Storefront(var state)) {
                    return new ShopRoute.Storefront(panelRef.get().capture(state));
                }
                return route;
            }

            @Override public void render(ShopRoute route) {
                if (route instanceof ShopRoute.Storefront(var state)) {
                    panelRef.get().load(state);
                }
            }
        });
        BuyerShopPanel panel = onEdt(() -> new BuyerShopPanel(
                client, navigator, new DefaultShopUiKit(), () -> { }));
        panelRef.set(panel);
        ShopProductQuery first = new ShopProductQuery(
                "shop-1", "本", "图书", new BigDecimal("20.00"), new BigDecimal("90.00"),
                ProductSortMode.SALES_DESC, 0, 1);

        onEdt(() -> navigator.openFromRoot(ShopRoute.defaultHome(), new ShopRoute.Storefront(
                new StorefrontViewState(first, 0))));
        flushUi();
        JButton next = component(panel, "storefront.pagination.next", JButton.class);
        assertThat(component(panel, "storefront.pagination.previous", JButton.class).isEnabled())
                .isFalse();

        onEdt((Runnable) next::doClick);
        flushUi();
        ShopProductQuery second = new ShopProductQuery(
                first.shopId(), first.keyword(), first.category(), first.minPrice(), first.maxPrice(),
                first.sortMode(), 1, first.pageSize());
        assertThat(client.shopQueries).containsExactly(first, second);
        assertThat(navigator.current()).contains(new ShopRoute.Storefront(
                new StorefrontViewState(second, 0)));
        assertThat(navigator.history()).containsExactly(ShopRoute.defaultHome());
    }

    private static void flushUi() throws Exception {
        flushEdt();
        flushEdt();
        flushEdt();
    }

    private static final class PagingClient implements ShopClientPort {
        private final List<HomeProductQuery> homeQueries = new ArrayList<>();
        private final List<ProductSearchQuery> searchQueries = new ArrayList<>();
        private final List<ShopProductQuery> shopQueries = new ArrayList<>();

        @Override public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
            homeQueries.add(query);
            return CompletableFuture.completedFuture(page(query.pageNumber(), query.pageSize()));
        }

        @Override public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) {
            searchQueries.add(query);
            return CompletableFuture.completedFuture(page(query.pageNumber(), query.pageSize()));
        }

        @Override public CompletableFuture<ShopDetail> getShop(String shopId) {
            return CompletableFuture.completedFuture(new ShopDetail(
                    shopId, "校园书店", "简介", "图书", "contact", ShopStatus.ACTIVE));
        }

        @Override public CompletableFuture<PageResult<ProductSummary>> getShopProducts(
                ShopProductQuery query) {
            shopQueries.add(query);
            return CompletableFuture.completedFuture(page(query.pageNumber(), query.pageSize()));
        }

        private static PageResult<ProductSummary> page(int page, int pageSize) {
            ProductSummary product = new ProductSummary(
                    "product-" + page, "shop-1", "校园书店", "商品 " + page, "图书",
                    new BigDecimal("30.00"), 10L - page,
                    Instant.parse("2026-08-30T00:00:00Z"));
            return new PageResult<>(List.of(product), page, pageSize, 3);
        }

        @Override public CompletableFuture<ProductDetail> getProduct(String productId) {
            return new CompletableFuture<>();
        }
        @Override public CompletableFuture<CartView> getCart() { return new CompletableFuture<>(); }
        @Override public CompletableFuture<PaidOrderHistory> getPaidOrders() {
            return new CompletableFuture<>();
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
        @Override public CompletableFuture<PaymentView> simulatePayment(
                SimulatePaymentCommand command) {
            return new CompletableFuture<>();
        }
    }
}
