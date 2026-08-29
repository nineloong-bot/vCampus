package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.DefaultShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSkuView;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductStatus;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.ShopStatus;
import edu.seu.vcampus.common.shop.ShopSummary;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogPanelsTest {
    @Test
    void rendersMinimumPriceAndIgnoresOlderSearchCompletion() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        ShopNavigator navigator = new ShopNavigator(route -> { });
        CompletableFuture<PageResult<ProductSummary>> first = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> second = new CompletableFuture<>();
        when(client.search(any())).thenReturn(first, second);
        ProductSearchPanel panel = onEdt(() ->
                new ProductSearchPanel(client, navigator, new DefaultShopUiKit(), () -> { }));
        ProductSearchQuery oldQuery = query("旧");
        ProductSearchQuery newQuery = query("新");

        onEdt(() -> panel.search(oldQuery));
        onEdt(() -> panel.search(newQuery));
        second.complete(page(summary("new", "new", "8.00")));
        first.complete(page(summary("old", "old", "3.00")));
        flushEdt();

        assertThat(panel.visibleProductNames()).containsExactly("new");
        assertThat(panel.visiblePrices()).containsExactly("¥8.00 起");
    }

    @Test
    void searchFormUsesEnteredFiltersAndSortWhenSubmitting() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.search(any())).thenReturn(pageFuture());
        ProductSearchPanel panel = onEdt(() -> new ProductSearchPanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> {
            component(panel, "keyword", JTextField.class).setText("铅笔");
            component(panel, "category", JTextField.class).setText("文具");
            component(panel, "min-price", JTextField.class).setText("2.50");
            component(panel, "max-price", JTextField.class).setText("9.00");
            component(panel, "sort", JComboBox.class).setSelectedItem(ProductSortMode.PRICE_DESC);
            component(panel, "search", JButton.class).doClick();
        });

        ArgumentCaptor<ProductSearchQuery> query = ArgumentCaptor.forClass(ProductSearchQuery.class);
        verify(client).search(query.capture());
        assertThat(query.getValue()).isEqualTo(new ProductSearchQuery("铅笔", "文具",
                new BigDecimal("2.50"), new BigDecimal("9.00"),
                ProductSortMode.PRICE_DESC, 0, 20));
    }

    @Test
    void homeUsesDefaultSalesQueryAndCardOpensProductRoute() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.home(any())).thenReturn(pageFuture(summary("product-9", "直尺", "4.00")));
        List<ShopRoute> routes = new ArrayList<>();
        ShopHomePanel panel = onEdt(() -> new ShopHomePanel(client,
                new ShopNavigator(routes::add), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.load());
        flushEdt();
        onEdt(() -> component(panel, "product-product-9", JButton.class).doClick());

        ArgumentCaptor<HomeProductQuery> query = ArgumentCaptor.forClass(HomeProductQuery.class);
        verify(client).home(query.capture());
        assertThat(query.getValue()).isEqualTo(new HomeProductQuery(null, null,
                ProductSortMode.SALES_DESC, 0, 20));
        assertThat(routes).containsExactly(new ShopRoute.Product("product-9"));
    }

    @Test
    void storefrontLoadsShopThenProductsWithSameShopId() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.getShop("shop-7")).thenReturn(CompletableFuture.completedFuture(new ShopDetail(
                "shop-7", "校园文具店", "简介", "文具", "123", ShopStatus.ACTIVE)));
        when(client.getShopProducts(any())).thenReturn(pageFuture());
        BuyerShopPanel panel = onEdt(() -> new BuyerShopPanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.load("shop-7"));
        flushEdt();

        ArgumentCaptor<ShopProductQuery> query = ArgumentCaptor.forClass(ShopProductQuery.class);
        verify(client).getShopProducts(query.capture());
        assertThat(query.getValue().shopId()).isEqualTo("shop-7");
    }

    @Test
    void addsExactAvailableSkuQuantityThenDisplaysCartCountAndOpensCart() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        ProductDetail detail = new ProductDetail("product-4", "笔记本", "文具", "简介",
                ProductStatus.ACTIVE, 0, new ShopSummary("shop-4", "学习用品店"), List.of(
                new ProductSkuView("sold-out", "缺货", new BigDecimal("5.00"), 0, true, 0),
                new ProductSkuView("inactive", "下架", new BigDecimal("6.00"), 8, false, 0),
                new ProductSkuView("sku-blue", "蓝色", new BigDecimal("7.00"), 9, true, 0)),
                Instant.EPOCH);
        when(client.getProduct("product-4")).thenReturn(CompletableFuture.completedFuture(detail));
        CartView cart = new CartView("cart-4", List.of(
                new edu.seu.vcampus.common.shop.CartItemView("cart-1", "product-4", "笔记本",
                        "sku-blue", "蓝色", "shop-4", "学习用品店", new BigDecimal("7.00"), 3, 0),
                new edu.seu.vcampus.common.shop.CartItemView("cart-2", "product-4", "笔记本",
                        "sku-red", "红色", "shop-4", "学习用品店", new BigDecimal("7.00"), 1, 0)),
                new BigDecimal("28.00"));
        when(client.addToCart(any())).thenReturn(CompletableFuture.completedFuture(cart));
        List<ShopRoute> routes = new ArrayList<>();
        ProductDetailPanel panel = onEdt(() -> new ProductDetailPanel(client,
                new ShopNavigator(routes::add), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.load("product-4"));
        flushEdt();
        assertThat(component(panel, "sku-description-sold-out", JLabel.class).getText())
                .contains("缺货");
        assertThat(component(panel, "sku-description-inactive", JLabel.class).getText())
                .contains("下架");
        onEdt(() -> {
            component(panel, "sku", JComboBox.class).setSelectedItem("sku-blue");
            component(panel, "quantity", JSpinner.class).setValue(3);
            component(panel, "add-to-cart", JButton.class).doClick();
        });
        flushEdt();
        onEdt(() -> component(panel, "open-cart", JButton.class).doClick());

        ArgumentCaptor<AddCartItemCommand> command = ArgumentCaptor.forClass(AddCartItemCommand.class);
        verify(client).addToCart(command.capture());
        assertThat(command.getValue()).isEqualTo(new AddCartItemCommand("sku-blue", 3));
        assertThat(panel.cartCount()).isEqualTo(4);
        assertThat(routes).containsExactly(new ShopRoute.Cart());
    }

    @Test
    void detailClearsOldProductDuringNewLoadAndOldSubmissionCannotUpdateNewProduct() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<ProductDetail> second = new CompletableFuture<>();
        CompletableFuture<CartView> submission = new CompletableFuture<>();
        when(client.getProduct("product-a")).thenReturn(CompletableFuture.completedFuture(detail(
                "product-a", "shop-a", "sku-a", "A")));
        when(client.getProduct("product-b")).thenReturn(second);
        when(client.addToCart(any())).thenReturn(submission);
        ProductDetailPanel panel = onEdt(() -> new ProductDetailPanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.load("product-a"));
        flushEdt();
        onEdt(() -> component(panel, "add-to-cart", JButton.class).doClick());
        onEdt(() -> panel.load("product-b"));

        assertThat(component(panel, "sku", JComboBox.class).isEnabled()).isFalse();
        assertThat(component(panel, "quantity", JSpinner.class).isEnabled()).isFalse();
        assertThat(component(panel, "store", JButton.class).isEnabled()).isFalse();
        assertThat(component(panel, "add-to-cart", JButton.class).isEnabled()).isFalse();
        assertThat(component(panel, "product-name", JLabel.class).getText()).isEmpty();
        submission.complete(new CartView("cart", List.of(
                new edu.seu.vcampus.common.shop.CartItemView("cart-a", "product-a", "A", "sku-a",
                        "A", "shop-a", "店A", new BigDecimal("3.00"), 9, 0)), new BigDecimal("27.00")));
        second.complete(detail("product-b", "shop-b", "sku-b", "B"));
        flushEdt();

        assertThat(component(panel, "product-name", JLabel.class).getText()).isEqualTo("B");
        assertThat(panel.cartCount()).isZero();
        assertThat(panel.visibleSkuIds()).containsExactly("sku-b");
    }

    @Test
    void storefrontPublishesShopAndProductsAtomicallyAcrossSecondStageOutcomes() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PageResult<ProductSummary>> aProducts = CompletableFuture.completedFuture(
                page(summary("product-a", "A", "3.00")));
        CompletableFuture<PageResult<ProductSummary>> bProducts = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> emptyProducts = CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 20, 0));
        CompletableFuture<PageResult<ProductSummary>> cProducts = CompletableFuture.completedFuture(
                page(summary("product-c", "C", "5.00")));
        when(client.getShop(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(new ShopDetail(
                invocation.getArgument(0), "店" + invocation.getArgument(0), "简介", "文具", "", ShopStatus.ACTIVE)));
        when(client.getShopProducts(any())).thenReturn(aProducts, bProducts, emptyProducts, cProducts);
        BuyerShopPanel panel = onEdt(() -> new BuyerShopPanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.load("a"));
        flushEdt();
        onEdt(() -> panel.load("b"));
        flushEdt();
        assertThat(component(panel, "shop-name", JLabel.class).getText()).isEmpty();
        assertThat(panel.visibleProductNames()).isEmpty();
        bProducts.completeExceptionally(new IllegalStateException("SHOP_UNAVAILABLE"));
        flushEdt();
        assertThat(component(panel, "shop-name", JLabel.class).getText()).isEmpty();

        onEdt(() -> panel.load("empty"));
        flushEdt();
        assertThat(component(panel, "shop-name", JLabel.class).getText()).isEmpty();
        onEdt(() -> panel.load("c"));
        flushEdt();
        flushEdt();
        assertThat(component(panel, "shop-name", JLabel.class).getText()).isEqualTo("店c");
        assertThat(component(panel, "product-product-c", JButton.class).getText()).contains("C");
    }

    @Test
    void latestHomeDetailAndDisposedSearchCompletionsDoNotPublishStaleResults() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PageResult<ProductSummary>> oldHome = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> newHome = new CompletableFuture<>();
        CompletableFuture<ProductDetail> oldDetail = new CompletableFuture<>();
        CompletableFuture<ProductDetail> newDetail = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> disposedSearch = new CompletableFuture<>();
        when(client.home(any())).thenReturn(oldHome, newHome);
        when(client.getProduct("old")).thenReturn(oldDetail);
        when(client.getProduct("new")).thenReturn(newDetail);
        when(client.search(any())).thenReturn(disposedSearch);
        ShopHomePanel home = onEdt(() -> new ShopHomePanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));
        ProductDetailPanel detail = onEdt(() -> new ProductDetailPanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));
        ProductSearchPanel search = onEdt(() -> new ProductSearchPanel(client,
                new ShopNavigator(route -> { }), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> { home.load(); home.load(); detail.load("old"); detail.load("new");
            search.search(query("disposed")); search.dispose(); });
        completeOffEdt(newHome, page(summary("new-home", "新首页", "4.00")));
        completeOffEdt(oldHome, page(summary("old-home", "旧首页", "3.00")));
        completeOffEdt(newDetail, detail("new", "shop-new", "sku-new", "新商品"));
        completeOffEdt(oldDetail, detail("old", "shop-old", "sku-old", "旧商品"));
        completeOffEdt(disposedSearch, page(summary("disposed", "不应显示", "2.00")));
        flushEdt();
        flushEdt();

        assertThat(home.visibleProductNames()).containsExactly("新首页");
        assertThat(component(detail, "product-name", JLabel.class).getText()).isEqualTo("新商品");
        assertThat(search.visibleProductNames()).isEmpty();
    }

    @Test
    void detailStoreButtonOpensTheLoadedStorefront() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.getProduct("product")).thenReturn(CompletableFuture.completedFuture(
                detail("product", "shop-loaded", "sku", "商品")));
        List<ShopRoute> routes = new ArrayList<>();
        ProductDetailPanel panel = onEdt(() -> new ProductDetailPanel(client,
                new ShopNavigator(routes::add), new DefaultShopUiKit(), () -> { }));

        onEdt(() -> panel.load("product"));
        flushEdt();
        onEdt(() -> component(panel, "store", JButton.class).doClick());

        assertThat(routes).containsExactly(new ShopRoute.Storefront("shop-loaded"));
    }

    private static ProductSearchQuery query(String keyword) {
        return new ProductSearchQuery(keyword, null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);
    }

    private static ProductSummary summary(String id, String name, String price) {
        return new ProductSummary(id, "shop-1", "文具店", name, "文具",
                new BigDecimal(price), 0, Instant.EPOCH);
    }

    private static ProductDetail detail(String productId, String shopId, String skuId, String name) {
        return new ProductDetail(productId, name, "文具", "简介", ProductStatus.ACTIVE, 0,
                new ShopSummary(shopId, "店" + shopId), List.of(new ProductSkuView(
                skuId, name, new BigDecimal("3.00"), 5, true, 0)), Instant.EPOCH);
    }

    private static <T> void completeOffEdt(CompletableFuture<T> future, T value) throws Exception {
        Thread worker = new Thread(() -> future.complete(value));
        worker.start();
        worker.join();
    }

    private static <T extends java.io.Serializable> PageResult<T> page(T value) {
        return new PageResult<>(List.of(value), 0, 20, 1);
    }

    @SafeVarargs
    private static <T extends java.io.Serializable> CompletableFuture<PageResult<T>> pageFuture(T... items) {
        return CompletableFuture.completedFuture(new PageResult<>(List.of(items), 0, 20, items.length));
    }
}
