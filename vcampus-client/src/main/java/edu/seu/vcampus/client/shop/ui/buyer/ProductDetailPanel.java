package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.CartCountModel;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSkuView;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.text.ParseException;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

/** Product information, SKU selection, and isolated cart submission lifecycle. */
public final class ProductDetailPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest loads = new LatestRequest();
    private final LatestRequest submissions = new LatestRequest();
    private final JLabel productName = named(new JLabel(), "product-name");
    private final CartCountModel cartCount;
    private final JComboBox<SkuChoice> sku = named(new JComboBox<>(), "sku");
    private final JSpinner quantity = named(new JSpinner(new SpinnerNumberModel(1, 1, 1, 1)), "quantity");
    private final JButton store;
    private final JButton addToCart;
    private final JButton openCart;
    private final JPanel skuDescriptions = new JPanel();
    private final JPanel actions;
    private final JPanel content = new JPanel(new BorderLayout(4, 4));
    private final Map<String, ProductSkuView> availableSkus = new LinkedHashMap<>();
    private String shopId;
    private String currentProductId;
    private long displayedLoad;
    private long cartRevision;
    public ProductDetailPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(client, navigator, uiKit, new CartCountModel(), sessionExpired);
    }

    public ProductDetailPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            CartCountModel cartCount, Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.cartCount = Objects.requireNonNull(cartCount, "cartCount");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        ShopComponentStyle.pagePanel(this);
        ShopComponentStyle.styleTextComponent(sku);
        ShopComponentStyle.styleTextComponent(quantity);
        store = uiKit.secondaryButton("store", "进入店铺");
        addToCart = uiKit.primaryButton("add-to-cart", "加入购物车");
        openCart = uiKit.secondaryButton("open-cart", "查看购物车");
        actions = uiKit.filterPanel("detail.actions", new FlowLayout(FlowLayout.LEFT));
        skuDescriptions.setLayout(new BoxLayout(skuDescriptions, BoxLayout.Y_AXIS));
        actions.add(store); actions.add(sku); actions.add(quantity); actions.add(addToCart);
        actions.add(openCart);
        store.addActionListener(event -> { if (shopId != null) navigator.open(new ShopRoute.Storefront(shopId)); });
        sku.addActionListener(event -> updateQuantityLimit());
        addToCart.addActionListener(event -> addSelectedSku());
        openCart.addActionListener(event -> navigator.open(new ShopRoute.Cart()));
        add(productName, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        productName.setFont(UiTypography.SECTION_TITLE);
        clearProduct();
        showDetail(ShopPageState.INITIAL, "");
    }

    public void load(String productId) {
        long request = loads.begin();
        cartCount.cancel(cartRevision);
        cartRevision = 0;
        submissions.begin();
        currentProductId = Objects.requireNonNull(productId, "productId");
        displayedLoad = 0;
        clearProduct();
        showDetail(ShopPageState.LOADING, "加载中…");
        client.getProduct(productId).whenComplete((detail, failure) -> finishLoad(request, detail, failure));
    }

    public int cartCount() { return cartCount.totalQuantity(); }
    public List<String> visibleSkuIds() { return List.copyOf(availableSkus.keySet()); }
    public void clearCartCount() {
        cartCount.clear();
    }
    public void dispose() {
        loads.dispose(); submissions.dispose(); cartCount.cancel(cartRevision);
    }

    private void finishLoad(long request, ProductDetail detail, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!loads.accepts(request)) return;
            if (failure != null) { showFailure(failure, () -> load(currentProductId)); return; }
            displayedLoad = request;
            productName.setText(detail.productName());
            shopId = detail.shop().shopId();
            for (ProductSkuView item : detail.skus()) {
                JLabel description = named(new JLabel("%s | ¥%s | 库存 %d | %s".formatted(
                        item.skuName(), item.unitPrice(), item.availableQuantity(),
                        item.active() ? "可售" : "已下架")), "sku-description-" + item.skuId());
                description.setAlignmentX(LEFT_ALIGNMENT);
                skuDescriptions.add(description);
                if (item.active() && item.availableQuantity() > 0) {
                    availableSkus.put(item.skuId(), item);
                    sku.addItem(new SkuChoice(item.skuId(), item.skuName()));
                }
            }
            store.setEnabled(true);
            setSkuControlsEnabled(!availableSkus.isEmpty());
            updateQuantityLimit();
            showDetail(ShopPageState.NORMAL, "");
        });
    }

    private void addSelectedSku() {
        SkuChoice choice = (SkuChoice) sku.getSelectedItem();
        ProductSkuView selected = choice == null ? null : availableSkus.get(choice.skuId());
        if (selected == null || displayedLoad == 0) return;
        int requestedQuantity;
        try {
            quantity.commitEdit();
            requestedQuantity = ((Number) quantity.getValue()).intValue();
        } catch (ParseException exception) {
            showDetail(ShopPageState.ERROR, "数量不能超过库存");
            return;
        }
        if (requestedQuantity < 1 || requestedQuantity > selected.availableQuantity()) {
            showDetail(ShopPageState.ERROR, "数量不能超过库存");
            return;
        }
        long request = submissions.begin();
        cartRevision = cartCount.beginUpdate();
        long submissionCartRevision = cartRevision;
        long loadAtSubmission = displayedLoad;
        addToCart.setEnabled(false);
        showDetail(ShopPageState.SUBMITTING, "正在加入购物车…");
        client.addToCart(new AddCartItemCommand(selected.skuId(), requestedQuantity))
                .whenComplete((cart, failure) -> finishAdd(
                        request, loadAtSubmission, submissionCartRevision, cart, failure));
    }

    private void finishAdd(long request, long loadAtSubmission, long cartRevision,
            CartView cart, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (failure == null) cartCount.update(cartRevision, cart);
            if (!submissions.accepts(request) || displayedLoad != loadAtSubmission) return;
            if (failure != null) { showFailure(failure, () -> load(currentProductId)); return; }
            setSkuControlsEnabled(!availableSkus.isEmpty());
            showDetail(ShopPageState.NORMAL, "");
        });
    }

    private void clearProduct() {
        productName.setText(""); shopId = null; availableSkus.clear(); sku.removeAllItems();
        skuDescriptions.removeAll(); quantity.setModel(new SpinnerNumberModel(1, 1, 1, 1));
        store.setEnabled(false); setSkuControlsEnabled(false);
    }

    private void updateQuantityLimit() {
        SkuChoice choice = (SkuChoice) sku.getSelectedItem();
        ProductSkuView selected = choice == null ? null : availableSkus.get(choice.skuId());
        if (selected == null) return;
        int maximum = (int) Math.min(Integer.MAX_VALUE, selected.availableQuantity());
        int current = Math.min((Integer) quantity.getValue(), maximum);
        quantity.setModel(new SpinnerNumberModel(Math.max(1, current), 1, maximum, 1));
    }

    private void setSkuControlsEnabled(boolean enabled) {
        sku.setEnabled(enabled); quantity.setEnabled(enabled); addToCart.setEnabled(enabled);
    }

    private void showDetail(ShopPageState state, String message) {
        content.removeAll();
        content.add(uiKit.stateView("detail.state", state, message, null), BorderLayout.NORTH);
        JPanel detail = uiKit.filterPanel("detail.normal", new BorderLayout(4, 4));
        detail.add(actions, BorderLayout.NORTH); detail.add(skuDescriptions, BorderLayout.CENTER);
        content.add(detail, BorderLayout.CENTER); refresh();
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { showState(ShopPageState.DISCONNECTED,
                ShopUiErrors.message(code), retry); sessionExpired.run(); }
        else showState(ShopPageState.ERROR, ShopUiErrors.message(code), retry);
    }

    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("detail.state", state, message, retry), BorderLayout.CENTER); refresh();
    }

    private void refresh() { content.revalidate(); content.repaint(); }
    private record SkuChoice(String skuId, String label) {
        @Override public String toString() { return label; }
    }
    private static <T extends Component> T named(T component, String name) { component.setName(name); return component; }
}
