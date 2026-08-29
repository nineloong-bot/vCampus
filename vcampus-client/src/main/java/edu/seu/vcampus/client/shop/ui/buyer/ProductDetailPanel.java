package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSkuView;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BoxLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Product information, valid SKU selection, and add-to-cart action. */
public final class ProductDetailPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final Runnable sessionExpired;
    private final ShopUiKit uiKit;
    private final LatestRequest latest = new LatestRequest();
    private final JLabel productName = new JLabel();
    private final JLabel error = new JLabel();
    private final JLabel cartCountLabel = new JLabel("购物车（0）");
    private final JComboBox<String> sku = named(new JComboBox<>(), "sku");
    private final JSpinner quantity = named(new JSpinner(new SpinnerNumberModel(1, 1, 1, 1)), "quantity");
    private final JButton store;
    private final JButton addToCart;
    private final JButton openCart;
    private final JPanel skuDescriptions = new JPanel();
    private final Map<String, ProductSkuView> availableSkus = new LinkedHashMap<>();
    private String shopId;
    private int cartCount;

    public ProductDetailPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.store = uiKit.secondaryButton("store", "店铺");
        this.addToCart = uiKit.primaryButton("add-to-cart", "加入购物车");
        this.openCart = uiKit.secondaryButton("open-cart", "查看购物车");
        productName.setName("product-name");
        error.setName("error");
        cartCountLabel.setName("cart-count");
        skuDescriptions.setLayout(new BoxLayout(skuDescriptions, BoxLayout.Y_AXIS));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(store);
        actions.add(sku);
        actions.add(quantity);
        actions.add(addToCart);
        actions.add(openCart);
        actions.add(cartCountLabel);
        store.addActionListener(event -> {
            if (shopId != null) {
                navigator.open(new ShopRoute.Storefront(shopId));
            }
        });
        sku.addActionListener(event -> updateQuantityLimit());
        addToCart.addActionListener(event -> addSelectedSku());
        openCart.addActionListener(event -> navigator.open(new ShopRoute.Cart()));
        add(productName, BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.add(actions, BorderLayout.NORTH);
        content.add(skuDescriptions, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        add(error, BorderLayout.SOUTH);
        setSkuControlsEnabled(false);
    }

    public void load(String productId) {
        long request = latest.begin();
        client.getProduct(Objects.requireNonNull(productId, "productId"))
                .whenComplete((detail, failure) -> finishLoad(request, detail, failure));
    }

    public int cartCount() {
        return cartCount;
    }

    public void dispose() {
        latest.dispose();
    }

    private void finishLoad(long request, ProductDetail detail, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) {
                return;
            }
            if (failure != null) {
                showFailure(failure);
                return;
            }
            productName.setText(detail.productName());
            shopId = detail.shop().shopId();
            store.setEnabled(true);
            availableSkus.clear();
            sku.removeAllItems();
            skuDescriptions.removeAll();
            for (ProductSkuView item : detail.skus()) {
                JLabel description = new JLabel("%s | ¥%s | 库存 %d | %s".formatted(
                        item.skuName(), item.unitPrice(), item.availableQuantity(),
                        item.active() ? "可售" : "已下架"));
                description.setName("sku-description-" + item.skuId());
                description.setAlignmentX(LEFT_ALIGNMENT);
                skuDescriptions.add(description);
                if (item.active() && item.availableQuantity() > 0) {
                    availableSkus.put(item.skuId(), item);
                    sku.addItem(item.skuId());
                }
            }
            setSkuControlsEnabled(!availableSkus.isEmpty());
            updateQuantityLimit();
            skuDescriptions.revalidate();
            skuDescriptions.repaint();
        });
    }

    private void addSelectedSku() {
        String skuId = (String) sku.getSelectedItem();
        ProductSkuView selected = availableSkus.get(skuId);
        if (selected == null) {
            return;
        }
        int selectedQuantity = (Integer) quantity.getValue();
        addToCart.setEnabled(false);
        long request = latest.begin();
        client.addToCart(new AddCartItemCommand(selected.skuId(), selectedQuantity))
                .whenComplete((cart, failure) -> finishAdd(request, cart, failure));
    }

    private void finishAdd(long request, CartView cart, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!latest.accepts(request)) {
                return;
            }
            addToCart.setEnabled(!availableSkus.isEmpty());
            if (failure != null) {
                showFailure(failure);
                return;
            }
            cartCount = cart.items().stream().mapToInt(item -> item.quantity()).sum();
            cartCountLabel.setText("购物车（" + cartCount + "）");
        });
    }

    private void updateQuantityLimit() {
        ProductSkuView selected = availableSkus.get(sku.getSelectedItem());
        if (selected == null) {
            return;
        }
        int maximum = Math.toIntExact(selected.availableQuantity());
        int current = Math.min((Integer) quantity.getValue(), maximum);
        quantity.setModel(new SpinnerNumberModel(Math.max(1, current), 1, maximum, 1));
    }

    private void setSkuControlsEnabled(boolean enabled) {
        sku.setEnabled(enabled);
        quantity.setEnabled(enabled);
        addToCart.setEnabled(enabled);
    }

    private void showFailure(Throwable failure) {
        String code = failureCode(failure);
        if ("AUTH_SESSION_EXPIRED".equals(code)) {
            sessionExpired.run();
        } else {
            error.setText(code);
        }
    }

    private static String failureCode(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "COMMON_INTERNAL_ERROR" : cause.getMessage();
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
