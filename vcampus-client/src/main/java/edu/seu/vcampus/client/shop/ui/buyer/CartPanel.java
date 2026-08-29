package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartItemView;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Cart page with independently guarded refresh and write lifecycles. */
public final class CartPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest loads = new LatestRequest();
    private final LatestRequest submissions = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private final Map<String, JButton> updateButtons = new HashMap<>();
    private final Map<String, JButton> removeButtons = new HashMap<>();
    private CartView cart;
    private long displayedLoad;
    private String busyItemId;
    private boolean updatingItem;

    public CartPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        add(content, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load() {
        long request = loads.begin();
        submissions.begin();
        showState(ShopPageState.LOADING, "加载中…", null);
        client.getCart().whenComplete((result, failure) -> finishLoad(request, result, failure));
    }

    public List<CartItemView> visibleItems() {
        return cart == null ? List.of() : cart.items();
    }

    public void updateQuantity(String cartItemId, int quantity) {
        CartItemView item = item(cartItemId);
        if (item == null || quantity < 1) return;
        JButton button = updateButtons.get(cartItemId);
        if (button == null || !button.isEnabled()) return;
        long request = submissions.begin();
        long loadAtSubmission = displayedLoad;
        busyItemId = cartItemId;
        updatingItem = true;
        button.setEnabled(false);
        showCart(ShopPageState.SUBMITTING, "正在更新购物车…");
        client.updateCartItem(new UpdateCartItemCommand(item.cartItemId(), quantity, item.rowVersion()))
                .whenComplete((result, failure) -> finishWrite(request, loadAtSubmission, button, result, failure));
    }

    public void remove(String cartItemId) {
        if (item(cartItemId) == null) return;
        JButton button = removeButtons.get(cartItemId);
        if (button == null || !button.isEnabled()) return;
        long request = submissions.begin();
        long loadAtSubmission = displayedLoad;
        busyItemId = cartItemId;
        updatingItem = false;
        button.setEnabled(false);
        showCart(ShopPageState.SUBMITTING, "正在移除商品…");
        client.removeCartItem(cartItemId)
                .whenComplete((result, failure) -> finishWrite(request, loadAtSubmission, button, result, failure));
    }

    public void disposePage() { loads.dispose(); submissions.dispose(); }
    public void dispose() { disposePage(); }

    private void finishLoad(long request, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!loads.accepts(request)) return;
            if (failure != null) { showFailure(failure, this::load); return; }
            displayedLoad = request;
            cart = result;
            if (cart.items().isEmpty()) showState(ShopPageState.EMPTY, "购物车为空", this::load);
            else showCart(ShopPageState.NORMAL, "");
        });
    }

    private void finishWrite(long request, long loadAtSubmission, JButton button, CartView result,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!submissions.accepts(request) || displayedLoad != loadAtSubmission) return;
            if (failure != null) {
                busyItemId = null;
                String code = ShopUiErrors.code(failure);
                if (ShopUiErrors.sessionExpired(code)) {
                    showState(ShopPageState.DISCONNECTED, code, this::load);
                    sessionExpired.run();
                } else showCart(ShopPageState.ERROR, code);
                return;
            }
            busyItemId = null;
            cart = result;
            if (cart.items().isEmpty()) showState(ShopPageState.EMPTY, "购物车为空", this::load);
            else showCart(ShopPageState.NORMAL, "");
        });
    }

    private void showCart(ShopPageState state, String message) {
        if (cart == null) { showState(state, message, this::load); return; }
        content.removeAll(); updateButtons.clear(); removeButtons.clear();
        JPanel normal = uiKit.filterPanel("cart.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("cart.state", state, message, null), BorderLayout.NORTH);
        JPanel rows = uiKit.filterPanel("cart.rows", new FlowLayout(FlowLayout.LEFT));
        for (CartItemView item : cart.items()) {
            JPanel row = uiKit.productCard("cart-item-" + item.cartItemId(), new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel("%s | ¥%s | %d".formatted(item.productName(),
                    item.displayedUnitPrice().toPlainString(), item.quantity())));
            JSpinner quantity = new JSpinner(new SpinnerNumberModel(item.quantity(), 1,
                    Integer.MAX_VALUE, 1));
            JButton update = uiKit.secondaryButton("cart.update-" + item.cartItemId(), "更新");
            JButton remove = uiKit.secondaryButton("cart.remove-" + item.cartItemId(), "删除");
            update.addActionListener(event -> updateQuantity(item.cartItemId(), (Integer) quantity.getValue()));
            remove.addActionListener(event -> remove(item.cartItemId()));
            if (item.cartItemId().equals(busyItemId)) {
                if (updatingItem) update.setEnabled(false);
                else remove.setEnabled(false);
            }
            updateButtons.put(item.cartItemId(), update); removeButtons.put(item.cartItemId(), remove);
            row.add(quantity); row.add(update); row.add(remove); rows.add(row);
        }
        JButton checkout = uiKit.primaryButton("cart.checkout", "去结算");
        checkout.addActionListener(event -> navigator.open(new ShopRoute.Checkout()));
        normal.add(rows, BorderLayout.CENTER); normal.add(checkout, BorderLayout.SOUTH);
        content.add(normal, BorderLayout.CENTER); refresh();
    }

    private CartItemView item(String id) {
        return cart == null ? null : cart.items().stream().filter(candidate -> candidate.cartItemId().equals(id))
                .findFirst().orElse(null);
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { showState(ShopPageState.DISCONNECTED, code, retry); sessionExpired.run(); }
        else showState(ShopPageState.ERROR, code, retry);
    }
    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("cart.state", state, message, retry), BorderLayout.CENTER); refresh();
    }
    private void refresh() { content.revalidate(); content.repaint(); }
}
