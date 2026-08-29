package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest latest = new LatestRequest();
    private final ProductCardsPanel cards;
    private final JPanel content = new JPanel(new BorderLayout());
    private final JTextField keyword = named(new JTextField(10), "keyword");
    private final JTextField category = named(new JTextField(8), "category");
    private final JTextField minPrice = named(new JTextField(6), "min-price");
    private final JTextField maxPrice = named(new JTextField(6), "max-price");
    private final JComboBox<ProductSortMode> sort = named(new JComboBox<>(ProductSortMode.values()), "sort");
    private final JButton searchButton;

    public ProductSearchPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.cards = new ProductCardsPanel(Objects.requireNonNull(navigator, "navigator"), uiKit);
        this.searchButton = uiKit.primaryButton("search", "搜索");
        JPanel filters = uiKit.filterPanel("search.filters", new FlowLayout(FlowLayout.LEFT));
        filters.add(keyword); filters.add(category); filters.add(minPrice); filters.add(maxPrice);
        filters.add(sort); filters.add(searchButton);
        searchButton.addActionListener(event -> submit());
        add(filters, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void search(ProductSearchQuery query) {
        long request = latest.begin();
        searchButton.setEnabled(false);
        showState(ShopPageState.LOADING, "加载中…", null);
        client.search(Objects.requireNonNull(query, "query"))
                .whenComplete((result, failure) -> finish(request, query, result, failure));
    }

    public List<String> visibleProductNames() { return cards.visibleProductNames(); }
    public List<String> visiblePrices() { return cards.visiblePrices(); }
    public void dispose() { latest.dispose(); }

    private void submit() {
        try {
            search(new ProductSearchQuery(value(keyword), value(category), decimal(minPrice), decimal(maxPrice),
                    (ProductSortMode) sort.getSelectedItem(), 0, 20));
        } catch (NumberFormatException error) {
            showState(ShopPageState.ERROR, "价格格式错误", null);
        }
    }

    private void finish(long request, ProductSearchQuery query, PageResult<ProductSummary> result,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) return;
            searchButton.setEnabled(true);
            if (failure != null) showFailure(failure, () -> search(query));
            else if (result.items().isEmpty()) showState(ShopPageState.EMPTY, "暂无商品", () -> search(query));
            else {
                uiKit.stateView("search.state", ShopPageState.NORMAL, "", null);
                cards.showProducts(result.items());
                content.removeAll(); content.add(cards, BorderLayout.CENTER); refresh();
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
        content.removeAll(); content.add(uiKit.stateView("search.state", state, message, retry), BorderLayout.CENTER);
        refresh();
    }

    private void refresh() { content.revalidate(); content.repaint(); }
    private static String value(JTextField field) { String value = field.getText().trim(); return value.isEmpty() ? null : value; }
    private static BigDecimal decimal(JTextField field) { String value = value(field); return value == null ? null : new BigDecimal(value); }
    private static String failureCode(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }
    private static <T extends Component> T named(T component, String name) { component.setName(name); return component; }
}
