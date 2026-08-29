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
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Cart page serializes writes so every authoritative cart response is published in request order. */
public final class CartPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest loads = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private final Map<String, JButton> updateButtons = new HashMap<>();
    private final Map<String, JButton> removeButtons = new HashMap<>();
    private final ArrayDeque<Write> queued = new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();
    private CartView cart;
    private long routeGeneration;
    private boolean disposed;
    private Write active;
    private boolean reloadAfterWrites;

    public CartPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client); this.navigator = Objects.requireNonNull(navigator);
        this.uiKit = Objects.requireNonNull(uiKit); this.sessionExpired = Objects.requireNonNull(sessionExpired);
        add(content, BorderLayout.CENTER); showState(ShopPageState.INITIAL, "", null);
    }
    public void load() {
        if (disposed) return;
        long request = loads.begin(); routeGeneration = request;
        showState(ShopPageState.LOADING, "加载中…", null);
        client.getCart().whenComplete((result, failure) -> finishLoad(request, result, failure));
    }
    public List<CartItemView> visibleItems() { return cart == null ? List.of() : cart.items(); }
    public void updateQuantity(String id, int quantity) { enqueue(new Write(true, id, quantity)); }
    public void remove(String id) { enqueue(new Write(false, id, 0)); }
    public void disposePage() { disposed = true; loads.dispose(); queued.clear(); queuedKeys.clear(); active = null; }
    public void dispose() { disposePage(); }

    private void enqueue(Write write) {
        if (disposed || item(write.id()) == null || write.update() && write.quantity() < 1 || queuedKeys.contains(write.key())) return;
        Write captured = new Write(write.update(), write.id(), write.quantity(), routeGeneration);
        queued.addLast(captured); queuedKeys.add(captured.key()); processNext();
    }
    private void processNext() {
        if (disposed || active != null || queued.isEmpty()) return;
        active = queued.removeFirst();
        CartItemView item = item(active.id());
        if (item == null) { finishWrite(active, null, null); return; }
        renderCart(ShopPageState.SUBMITTING, active.update() ? "正在更新购物车…" : "正在移除商品…");
        if (active.update()) client.updateCartItem(new UpdateCartItemCommand(item.cartItemId(), active.quantity(), item.rowVersion()))
                .whenComplete((result, failure) -> finishWrite(active, result, failure));
        else client.removeCartItem(active.id()).whenComplete((result, failure) -> finishWrite(active, result, failure));
    }
    private void finishLoad(long request, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || !loads.accepts(request)) return;
            if (active != null || !queued.isEmpty()) { reloadAfterWrites = true; return; }
            if (failure != null) { showFailure(failure); return; }
            cart = result; renderCart(ShopPageState.NORMAL, "");
        });
    }
    private void finishWrite(Write write, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || active != write) return;
            active = null; queuedKeys.remove(write.key());
            if (failure == null && result != null && write.generation() == routeGeneration) cart = result;
            if (failure != null) showWriteFailure(failure); else renderCart(ShopPageState.NORMAL, "");
            if (!queued.isEmpty()) processNext();
            else if (reloadAfterWrites) { reloadAfterWrites = false; load(); }
        });
    }
    private void showWriteFailure(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { showState(ShopPageState.DISCONNECTED, code, this::load); sessionExpired.run(); }
        else renderCart(ShopPageState.ERROR, code);
    }
    private void showFailure(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { showState(ShopPageState.DISCONNECTED, code, this::load); sessionExpired.run(); }
        else showState(ShopPageState.ERROR, code, this::load);
    }
    private void renderCart(ShopPageState state, String message) {
        if (cart == null || cart.items().isEmpty()) { showState(ShopPageState.EMPTY, "购物车为空", this::load); return; }
        content.removeAll(); updateButtons.clear(); removeButtons.clear();
        JPanel normal = uiKit.filterPanel("cart.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("cart.state", state, message, null), BorderLayout.NORTH);
        JPanel rows = uiKit.filterPanel("cart.rows", new FlowLayout(FlowLayout.LEFT));
        for (CartItemView item : cart.items()) {
            JPanel row = uiKit.productCard("cart-item-" + item.cartItemId(), new FlowLayout(FlowLayout.LEFT));
            row.add(new JLabel("%s | ¥%s | %d".formatted(item.productName(), item.displayedUnitPrice().toPlainString(), item.quantity())));
            JSpinner quantity = new JSpinner(new SpinnerNumberModel(item.quantity(), 1, Integer.MAX_VALUE, 1));
            JButton update = uiKit.secondaryButton("cart.update-" + item.cartItemId(), "更新");
            JButton remove = uiKit.secondaryButton("cart.remove-" + item.cartItemId(), "删除");
            update.addActionListener(e -> updateQuantity(item.cartItemId(), (Integer) quantity.getValue())); remove.addActionListener(e -> remove(item.cartItemId()));
            if (active != null && active.id().equals(item.cartItemId())) (active.update() ? update : remove).setEnabled(false);
            updateButtons.put(item.cartItemId(), update); removeButtons.put(item.cartItemId(), remove);
            row.add(quantity); row.add(update); row.add(remove); rows.add(row);
        }
        JButton checkout = uiKit.primaryButton("cart.checkout", "去结算"); checkout.addActionListener(e -> navigator.open(new ShopRoute.Checkout()));
        normal.add(rows, BorderLayout.CENTER); normal.add(checkout, BorderLayout.SOUTH); content.add(normal, BorderLayout.CENTER); refresh();
    }
    private CartItemView item(String id) { return cart == null ? null : cart.items().stream().filter(i -> i.cartItemId().equals(id)).findFirst().orElse(null); }
    private void showState(ShopPageState state, String message, Runnable retry) { content.removeAll(); content.add(uiKit.stateView("cart.state", state, message, retry), BorderLayout.CENTER); refresh(); }
    private void refresh() { content.revalidate(); content.repaint(); }
    private record Write(boolean update, String id, int quantity, long generation) {
        private Write(boolean update, String id, int quantity) { this(update, id, quantity, 0); }
        String key() { return (update ? "U:" : "R:") + id; }
    }
}
