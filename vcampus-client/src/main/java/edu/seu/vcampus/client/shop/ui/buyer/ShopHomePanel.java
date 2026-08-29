package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Objects;

/** Buyer catalog landing page. */
public final class ShopHomePanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest latest = new LatestRequest();
    private final ProductCardsPanel cards;
    private final JPanel content = new JPanel(new BorderLayout());

    public ShopHomePanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.cards = new ProductCardsPanel(navigator, uiKit);
        JButton search = uiKit.primaryButton("home.search", "搜索商品");
        search.addActionListener(event -> navigator.open(new ShopRoute.Search(new ProductSearchQuery(
                null, null, null, null, ProductSortMode.SALES_DESC, 0, 20))));
        add(search, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load() { load(new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20)); }

    public void load(HomeProductQuery query) {
        long request = latest.begin();
        showState(ShopPageState.LOADING, "加载中…", null);
        client.home(Objects.requireNonNull(query, "query"))
                .whenComplete((result, failure) -> finish(request, query, result, failure));
    }

    public void dispose() { latest.dispose(); }
    public List<String> visibleProductNames() { return cards.visibleProductNames(); }

    private void finish(long request, HomeProductQuery query, PageResult<ProductSummary> result,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) return;
            if (failure != null) showFailure(failure, () -> load(query));
            else if (result.items().isEmpty()) showState(ShopPageState.EMPTY, "暂无商品", () -> load(query));
            else {
                cards.showProducts(result.items());
                content.removeAll();
                JPanel normal = uiKit.filterPanel("home.normal", new BorderLayout());
                normal.add(uiKit.stateView("home.state", ShopPageState.NORMAL, "", null), BorderLayout.NORTH);
                normal.add(cards, BorderLayout.CENTER);
                content.add(normal, BorderLayout.CENTER);
                refresh();
            }
        });
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = failureCode(failure);
        if ("AUTH_SESSION_EXPIRED".equals(code)) {
            showState(ShopPageState.DISCONNECTED, code, retry);
            sessionExpired.run();
        } else showState(ShopPageState.ERROR, code, retry);
    }

    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll();
        content.add(uiKit.stateView("home.state", state, message, retry), BorderLayout.CENTER);
        refresh();
    }

    private void refresh() { content.revalidate(); content.repaint(); }

    private static String failureCode(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }
}
