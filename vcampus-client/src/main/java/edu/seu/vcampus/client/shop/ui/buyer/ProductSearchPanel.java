package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.navigation.SearchViewState;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Search form and result cards for the buyer catalog. */
public final class ProductSearchPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest latest = new LatestRequest();
    private final ProductCardsPanel cards;
    private final ShopPaginationPanel pagination;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JScrollPane scroll = named(new JScrollPane(content), "search.scroll");
    private final JTextField keyword = new ShopSearchField(18, "keyword");
    private final JComboBox<String> category = named(new JComboBox<>(new String[] {
            "全部", "文具", "图书", "生活用品", "药品"
    }), "category");
    private final JTextField minPrice = named(new JTextField(6), "min-price");
    private final JTextField maxPrice = named(new JTextField(6), "max-price");
    private final JComboBox<ProductSortMode> sort = named(new JComboBox<>(ProductSortMode.values()), "sort");
    private final JButton searchButton;
    private final JButton filterToggle;
    private final JButton filterButton;
    private final JPanel filters;
    private boolean searched;
    private boolean restoring;

    public ProductSearchPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.cards = new ProductCardsPanel(navigator, uiKit);
        this.pagination = new ShopPaginationPanel("search", uiKit);
        navigator.addListener(route -> {
            if (!(route instanceof ShopRoute.Search)) latest.begin();
        });
        this.searchButton = uiKit.primaryButton("search", "搜索");
        this.filterToggle = uiKit.secondaryButton("search.filters.toggle", "筛选");
        this.filterButton = uiKit.secondaryButton("search.filter", "应用筛选");
        this.filters = uiKit.filterPanel("search.filters", new FlowLayout(FlowLayout.LEFT));
        filters.add(label("分类", "search.category.label", category));
        filters.add(category);
        filters.add(label("最低价", "search.min-price.label", minPrice));
        filters.add(minPrice);
        filters.add(label("最高价", "search.max-price.label", maxPrice));
        filters.add(maxPrice);
        filters.add(label("排序方式", "search.sort.label", sort));
        filters.add(sort);
        filters.add(filterButton);
        searchButton.addActionListener(event -> submit());
        filterToggle.addActionListener(event -> submit());
        filterButton.addActionListener(event -> submit());
        sort.addItemListener(event -> {
            if (!restoring && event.getStateChange() == java.awt.event.ItemEvent.SELECTED && searched) {
                submit();
            }
        });
        JPanel primary = uiKit.filterPanel("search.primary", new FlowLayout(FlowLayout.LEFT));
        primary.add(keyword); primary.add(searchButton); primary.add(filterToggle);
        JPanel controls = new JPanel(new BorderLayout(0, 4));
        controls.add(primary, BorderLayout.NORTH); controls.add(filters, BorderLayout.CENTER);
        add(controls, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        filterToggle.setVisible(false);
        filters.setVisible(false);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void search(ProductSearchQuery query) {
        search(new SearchViewState(query, false, false, 0));
    }

    public void search(SearchViewState state) {
        SearchViewState requested = Objects.requireNonNull(state, "state");
        searched = requested.searched();
        restoreFilters(requested);
        filterToggle.setVisible(searched);
        long request = latest.begin();
        searchButton.setEnabled(false);
        showState(ShopPageState.LOADING, "加载中…", null);
        client.search(requested.query())
                .whenComplete((result, failure) -> finish(request, requested, result, failure));
    }

    public SearchViewState capture(SearchViewState state) {
        SearchViewState captured = new SearchViewState(state.query(), searched,
                true, scroll.getVerticalScrollBar().getValue());
        latest.begin();
        return captured;
    }

    public List<String> visibleProductNames() { return cards.visibleProductNames(); }
    public List<String> visiblePrices() { return cards.visiblePrices(); }
    public void dispose() { latest.dispose(); }

    private void submit() {
        try {
            int pageSize = navigator.current().orElse(null) instanceof ShopRoute.Search current
                    ? current.state().query().pageSize()
                    : 20;
            ProductSearchQuery query = new ProductSearchQuery(value(keyword), category(),
                    decimal(minPrice), decimal(maxPrice), (ProductSortMode) sort.getSelectedItem(),
                    0, pageSize);
            SearchViewState state = new SearchViewState(
                    query, searched, filters.isVisible(), 0);
            if (navigator.current().orElse(null) instanceof ShopRoute.Search) {
                navigator.replaceCurrent(new ShopRoute.Search(state));
            } else {
                navigator.open(new ShopRoute.Search(state));
            }
        } catch (NumberFormatException error) {
            showState(ShopPageState.ERROR, "价格格式错误", null);
        }
    }

    private void finish(long request, SearchViewState state, PageResult<ProductSummary> result,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) return;
            searchButton.setEnabled(true);
            if (failure != null) showFailure(failure, () -> search(state));
            else {
                searched = true;
                filterToggle.setVisible(true);
                filters.setVisible(true);
                if (result.items().isEmpty()) {
                    showState(ShopPageState.EMPTY, "暂无商品", () -> search(state));
                    pagination.showPage(result, page -> openPage(state, page));
                    content.add(pagination, BorderLayout.SOUTH);
                    refresh();
                } else {
                    cards.showProducts(result.items());
                    content.removeAll();
                    JPanel normal = uiKit.filterPanel("search.normal", new BorderLayout());
                    normal.add(uiKit.stateView("search.state", ShopPageState.NORMAL, "", null), BorderLayout.NORTH);
                    normal.add(cards, BorderLayout.CENTER);
                    pagination.showPage(result, page -> openPage(state, page));
                    normal.add(pagination, BorderLayout.SOUTH);
                    content.add(normal, BorderLayout.CENTER);
                    refresh();
                }
            }
            SwingUtilities.invokeLater(() -> {
                if (latest.accepts(request)) {
                    scroll.getVerticalScrollBar().setValue(state.scrollY());
                }
            });
        });
    }

    private void openPage(SearchViewState state, int page) {
        ProductSearchQuery query = state.query();
        ProductSearchQuery paged = new ProductSearchQuery(query.keyword(), query.category(),
                query.minPrice(), query.maxPrice(), query.sortMode(), page, query.pageSize());
        navigator.replaceCurrent(new ShopRoute.Search(new SearchViewState(
                paged, true, true, 0)));
    }

    private void restoreFilters(SearchViewState state) {
        ProductSearchQuery query = state.query();
        restoring = true;
        try {
            keyword.setText(text(query.keyword()));
            category.setSelectedItem(query.category() == null ? "全部" : query.category());
            minPrice.setText(text(query.minPrice())); maxPrice.setText(text(query.maxPrice()));
            sort.setSelectedItem(query.sortMode());
        } finally {
            restoring = false;
        }
        filters.setVisible(state.searched());
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = failureCode(failure);
        if ("AUTH_SESSION_EXPIRED".equals(code)) {
            showState(ShopPageState.DISCONNECTED, code, retry);
            sessionExpired.run();
        } else showState(ShopPageState.ERROR, code, retry);
    }

    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("search.state", state, message, retry), BorderLayout.CENTER);
        refresh();
    }

    private void refresh() { content.revalidate(); content.repaint(); }
    private static String value(JTextField field) { String value = field.getText().trim(); return value.isEmpty() ? null : value; }
    private String category() {
        Object value = category.getSelectedItem();
        return value == null || "全部".equals(value) ? null : value.toString();
    }
    private static BigDecimal decimal(JTextField field) { String value = value(field); return value == null ? null : new BigDecimal(value); }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static String failureCode(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }
    private static JLabel label(String text, String name, Component target) {
        JLabel label = named(new JLabel(text), name);
        label.setLabelFor(target);
        return label;
    }
    private static <T extends Component> T named(T component, String name) { component.setName(name); return component; }
}
