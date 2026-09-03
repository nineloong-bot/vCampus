package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.HomeViewState;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
    private final ShopPaginationPanel pagination;
    private final JPanel content = new JPanel();
    private final JScrollPane scroll = named(new JScrollPane(content), "home.scroll");
    private final JTextField keyword = new ShopSearchField(18, "home.keyword");
    private final JPanel results = named(new JPanel(new BorderLayout()), "home.results");

    public ShopHomePanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.cards = new ProductCardsPanel(navigator, uiKit, ProductCardContext.HOME);
        this.pagination = new ShopPaginationPanel("home", uiKit);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        ShopComponentStyle.pagePanel(this);
        navigator.addListener(route -> {
            if (!(route instanceof ShopRoute.Home)) latest.begin();
        });
        JButton search = uiKit.primaryButton("home.search", "搜索");
        search.addActionListener(event -> navigator.open(new ShopRoute.Search(new SearchViewState(
                new ProductSearchQuery(value(keyword), null, null, null,
                        ProductSortMode.SALES_DESC, 0, 20), false, false, 0))));
        JPanel searchBar = uiKit.filterPanel("home.search-bar", new FlowLayout(FlowLayout.LEFT));
        searchBar.add(keyword); searchBar.add(search);
        JPanel categories = uiKit.filterPanel("home.categories", new FlowLayout(FlowLayout.LEFT));
        for (String category : List.of("文具", "图书", "生活用品", "药品", "其他")) {
            JButton button = uiKit.secondaryButton("home.category." + category, category);
            button.addActionListener(event -> navigator.open(new ShopRoute.Search(new SearchViewState(
                    new ProductSearchQuery(null, category, null, null,
                            ProductSortMode.SALES_DESC, 0, 20), false, false, 0))));
            categories.add(button);
        }
        content.add(searchBar);
        content.add(categories);
        JLabel recommendationTitle = named(new JLabel("猜你喜欢"), "home.recommendations");
        recommendationTitle.setFont(UiTypography.SECTION_TITLE);
        content.add(recommendationTitle);
        content.add(results);
        add(scroll, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load() { load(new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20)); }

    public void load(HomeProductQuery query) {
        load(new HomeViewState(query, 0));
    }

    public void load(HomeViewState state) {
        long request = latest.begin();
        showState(ShopPageState.LOADING, "加载中…", null);
        HomeViewState requested = Objects.requireNonNull(state, "state");
        client.home(requested.query())
                .whenComplete((result, failure) -> finish(request, requested, result, failure));
    }

    public HomeViewState capture(HomeViewState state) {
        HomeViewState captured = new HomeViewState(
                state.query(), scroll.getVerticalScrollBar().getValue());
        latest.begin();
        return captured;
    }

    public void dispose() { latest.dispose(); }
    public List<String> visibleProductNames() { return cards.visibleProductNames(); }

    private void finish(long request, HomeViewState state, PageResult<ProductSummary> result,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) return;
            if (failure != null) showFailure(failure, () -> load(state));
            else if (result.items().isEmpty()) {
                showState(ShopPageState.EMPTY, "暂无商品", () -> load(state));
                pagination.showPage(result, page -> openPage(state, page));
                results.add(pagination, BorderLayout.SOUTH);
                refresh();
            } else {
                cards.showProducts(result.items());
                results.removeAll();
                JPanel normal = uiKit.filterPanel("home.normal", new BorderLayout());
                normal.add(uiKit.stateView("home.state", ShopPageState.NORMAL, "", null),
                        BorderLayout.NORTH);
                normal.add(cards, BorderLayout.CENTER);
                pagination.showPage(result, page -> openPage(state, page));
                normal.add(pagination, BorderLayout.SOUTH);
                results.add(normal, BorderLayout.CENTER);
                refresh();
            }
            SwingUtilities.invokeLater(() -> {
                if (latest.accepts(request)) {
                    scroll.getVerticalScrollBar().setValue(state.scrollY());
                }
            });
        });
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) {
            showState(ShopPageState.DISCONNECTED, ShopUiErrors.message(code), retry);
            sessionExpired.run();
        } else showState(ShopPageState.ERROR, ShopUiErrors.message(code), retry);
    }

    private void showState(ShopPageState state, String message, Runnable retry) {
        results.removeAll();
        results.add(uiKit.stateView("home.state", state, message, retry), BorderLayout.CENTER);
        refresh();
    }

    private void refresh() { content.revalidate(); content.repaint(); }

    private static String value(JTextField field) {
        String value = field.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private void openPage(HomeViewState state, int page) {
        HomeProductQuery query = state.query();
        HomeProductQuery paged = new HomeProductQuery(query.minPrice(), query.maxPrice(),
                query.sortMode(), page, query.pageSize());
        navigator.replaceCurrent(new ShopRoute.Home(new HomeViewState(paged, 0)));
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name); return component;
    }
}
