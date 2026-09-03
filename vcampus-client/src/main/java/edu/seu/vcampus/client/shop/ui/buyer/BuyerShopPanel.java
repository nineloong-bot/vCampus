package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.StorefrontViewState;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Objects;

/** Storefront page atomically publishing a shop header and its product catalog. */
public final class BuyerShopPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopUiKit uiKit;
    private final ShopNavigator navigator;
    private final Runnable sessionExpired;
    private final LatestRequest latest = new LatestRequest();
    private final JLabel shopName = named(new JLabel(), "shop-name");
    private final ProductCardsPanel cards;
    private final ShopPaginationPanel pagination;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JScrollPane scroll = named(new JScrollPane(content), "storefront.scroll");
    private String currentShopId;

    public BuyerShopPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        ShopComponentStyle.pagePanel(this);
        ShopNavigator routes = this.navigator;
        cards = new ProductCardsPanel(routes, uiKit, ProductCardContext.STOREFRONT);
        pagination = new ShopPaginationPanel("storefront", uiKit);
        routes.addListener(route -> {
            if (!(route instanceof ShopRoute.Storefront)) latest.begin();
        });
        add(shopName, BorderLayout.NORTH); add(scroll, BorderLayout.CENTER);
        shopName.setFont(UiTypography.SECTION_TITLE);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load(String shopId) {
        load(new StorefrontViewState(new ShopProductQuery(shopId, null, null, null, null,
                ProductSortMode.SALES_DESC, 0, 20), 0));
    }

    public void load(StorefrontViewState state) {
        StorefrontViewState requested = Objects.requireNonNull(state, "state");
        String shopId = requested.query().shopId();
        long request = latest.begin();
        currentShopId = Objects.requireNonNull(shopId, "shopId");
        shopName.setText(""); cards.showProducts(List.of());
        showState(ShopPageState.LOADING, "加载中…", null);
        client.getShop(shopId).whenComplete((shop, failure) ->
                afterShop(request, requested, shop, failure));
    }

    public StorefrontViewState capture(StorefrontViewState state) {
        StorefrontViewState captured = new StorefrontViewState(
                state.query(), scroll.getVerticalScrollBar().getValue());
        latest.begin();
        return captured;
    }

    public List<String> visibleProductNames() { return cards.visibleProductNames(); }
    public void dispose() { latest.dispose(); }

    private void afterShop(long request, StorefrontViewState state, ShopDetail shop, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) return;
            if (failure != null) { showFailure(failure, () -> load(state)); return; }
            client.getShopProducts(state.query())
                    .whenComplete((products, productFailure) ->
                            finish(request, state, shop, products, productFailure));
        });
    }

    private void finish(long request, StorefrontViewState state, ShopDetail shop,
            PageResult<ProductSummary> products,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) return;
            if (failure != null) { showFailure(failure, () -> load(state)); return; }
            shopName.setText(shop.shopName());
            if (products.items().isEmpty()) {
                showState(ShopPageState.EMPTY, "暂无商品", () -> load(state));
                pagination.showPage(products, page -> openPage(state, page));
                content.add(pagination, BorderLayout.SOUTH);
                refresh();
                restoreScroll(request, state);
                return;
            }
            cards.showProducts(products.items());
            content.removeAll();
            JPanel normal = uiKit.filterPanel("storefront.normal", new BorderLayout());
            normal.add(uiKit.stateView("storefront.state", ShopPageState.NORMAL, "", null), BorderLayout.NORTH);
            normal.add(cards, BorderLayout.CENTER);
            pagination.showPage(products, page -> openPage(state, page));
            normal.add(pagination, BorderLayout.SOUTH);
            content.add(normal, BorderLayout.CENTER); refresh();
            restoreScroll(request, state);
        });
    }

    private void openPage(StorefrontViewState state, int page) {
        ShopProductQuery query = state.query();
        ShopProductQuery paged = new ShopProductQuery(query.shopId(), query.keyword(),
                query.category(), query.minPrice(), query.maxPrice(), query.sortMode(),
                page, query.pageSize());
        navigator.replaceCurrent(new ShopRoute.Storefront(new StorefrontViewState(paged, 0)));
    }

    private void restoreScroll(long request, StorefrontViewState state) {
        SwingUtilities.invokeLater(() -> {
            if (latest.accepts(request)) {
                scroll.getVerticalScrollBar().setValue(state.scrollY());
            }
        });
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { showState(ShopPageState.DISCONNECTED,
                ShopUiErrors.message(code), retry); sessionExpired.run(); }
        else showState(ShopPageState.ERROR, ShopUiErrors.message(code), retry);
    }

    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("storefront.state", state, message, retry), BorderLayout.CENTER); refresh();
    }

    private void refresh() { content.revalidate(); content.repaint(); }
    private static <T extends java.awt.Component> T named(T component, String name) { component.setName(name); return component; }
}
