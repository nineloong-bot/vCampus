package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.CartCountModel;
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
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import edu.seu.vcampus.client.shop.ui.catalog.WrappingGridLayout;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.math.BigDecimal;

/** Cart page serializes writes so every authoritative cart response is published in request order. */
public final class CartPanel extends JPanel {
    @FunctionalInterface
    interface RemovePrompt { boolean confirm(String message); }

    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final RemovePrompt removePrompt;
    private final CartCountModel cartCount;
    private final LatestRequest loads = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private final Map<String, JButton> updateButtons = new HashMap<>();
    private final Map<String, JButton> removeButtons = new HashMap<>();
    private final ArrayDeque<Write> queued = new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();
    private final Set<String> selectedItemIds = new LinkedHashSet<>();
    private CartView cart;
    private long routeGeneration;
    private boolean disposed;
    private Write active;
    private boolean reloadAfterWrites;
    private boolean disconnected;
    private boolean routeActive = true;
    private long loadCartRevision;

    public CartPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit, Runnable sessionExpired) {
        this(client, navigator, uiKit, new CartCountModel(), sessionExpired);
    }
    public CartPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            CartCountModel cartCount, Runnable sessionExpired) {
        this(client, navigator, uiKit, cartCount, sessionExpired,
                message -> JOptionPane.showConfirmDialog(null, message, "校园商城",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                        == JOptionPane.YES_OPTION);
    }
    CartPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            CartCountModel cartCount, Runnable sessionExpired, RemovePrompt removePrompt) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client); this.navigator = Objects.requireNonNull(navigator);
        this.uiKit = Objects.requireNonNull(uiKit); this.cartCount = Objects.requireNonNull(cartCount);
        this.sessionExpired = Objects.requireNonNull(sessionExpired);
        this.removePrompt = Objects.requireNonNull(removePrompt);
        navigator.addListener(route -> {
            if (!(route instanceof ShopRoute.Cart)) leaveRoute();
        });
        add(content, BorderLayout.CENTER); showState(ShopPageState.INITIAL, "", null);
    }
    public void load() {
        if (disposed || disconnected) return;
        routeActive = true;
        long request = loads.begin(); routeGeneration = request;
        showState(ShopPageState.LOADING, "加载中…", null);
        if (active != null || !queued.isEmpty()) { reloadAfterWrites = true; return; }
        long cartRevision = cartCount.beginUpdate();
        loadCartRevision = cartRevision;
        client.getCart().whenComplete((result, failure) ->
                finishLoad(request, cartRevision, result, failure));
    }
    public List<CartItemView> visibleItems() { return cart == null ? List.of() : cart.items(); }
    public Set<String> selectedItemIds() { return Set.copyOf(selectedItemIds); }
    public void updateQuantity(String id, int quantity) { enqueue(new Write(true, id, quantity)); }
    public void remove(String id) { enqueue(new Write(false, id, 0)); }
    public void disposePage() { disposed = true; loads.dispose(); queued.clear(); queuedKeys.clear(); active = null; }
    public void dispose() { disposePage(); }

    private void enqueue(Write write) {
        if (disposed || disconnected || item(write.id()) == null || write.update() && write.quantity() < 1 || queuedKeys.contains(write.key())) return;
        Write captured = new Write(write.update(), write.id(), write.quantity(), routeGeneration);
        queued.addLast(captured); queuedKeys.add(captured.key());
        JButton initiating = write.update() ? updateButtons.get(write.id()) : removeButtons.get(write.id());
        if (initiating != null) initiating.setEnabled(false);
        processNext();
    }
    private void processNext() {
        if (disposed || active != null || queued.isEmpty()) return;
        active = queued.removeFirst();
        CartItemView item = item(active.id());
        if (item == null) { finishWrite(active, null, null); return; }
        if (active.generation() != routeGeneration) { finishWrite(active, null, null); return; }
        renderCart(ShopPageState.SUBMITTING, active.update() ? "正在更新购物车…" : "正在移除商品…");
        long cartRevision = cartCount.beginUpdate();
        if (active.update()) client.updateCartItem(new UpdateCartItemCommand(item.cartItemId(), active.quantity(), item.rowVersion()))
                .whenComplete((result, failure) -> finishWrite(active, cartRevision, result, failure));
        else client.removeCartItem(active.id()).whenComplete((result, failure) ->
                finishWrite(active, cartRevision, result, failure));
    }
    private void finishLoad(long request, long cartRevision, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || disconnected || !loads.accepts(request)) return;
            if (active != null || !queued.isEmpty()) { reloadAfterWrites = true; return; }
            if (failure != null) { showFailure(failure); return; }
            cart = result; cartCount.update(cartRevision, result); renderCart(ShopPageState.NORMAL, "");
        });
    }
    private void finishWrite(Write write, CartView result, Throwable failure) {
        finishWrite(write, 0, result, failure);
    }
    private void finishWrite(Write write, long cartRevision, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || disconnected || active != write) return;
            active = null; queuedKeys.remove(write.key());
            boolean current = write.generation() == routeGeneration;
            if (failure == null && result != null) {
                if (cartRevision != 0) cartCount.update(cartRevision, result);
                if (current) cart = result;
            }
            if (failure != null) {
                if (showWriteFailure(failure, current)) return;
            } else if (current) renderCart(ShopPageState.NORMAL, "");
            if (!queued.isEmpty()) processNext();
            else if (routeActive && (reloadAfterWrites || !current)) { reloadAfterWrites = false; load(); }
        });
    }

    private void leaveRoute() {
        if (!routeActive) return;
        routeActive = false;
        routeGeneration = loads.begin();
        cartCount.cancel(loadCartRevision);
        loadCartRevision = 0;
        reloadAfterWrites = false;
    }
    private boolean showWriteFailure(Throwable failure, boolean current) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { disconnect(code); return true; }
        if (current) renderCart(ShopPageState.ERROR, ShopUiErrors.message(code));
        return false;
    }
    private void showFailure(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) disconnect(code);
        else showState(ShopPageState.ERROR, ShopUiErrors.message(code), this::load);
    }
    private void disconnect(String code) {
        if (disconnected) return;
        disconnected = true; loads.dispose(); queued.clear(); queuedKeys.clear(); reloadAfterWrites = false;
        showState(ShopPageState.DISCONNECTED, ShopUiErrors.message(code), null);
        sessionExpired.run();
    }
    private void renderCart(ShopPageState state, String message) {
        if (cart == null || cart.items().isEmpty()) { showState(ShopPageState.EMPTY, "购物车为空", this::load); return; }
        selectedItemIds.retainAll(cart.items().stream().map(CartItemView::cartItemId).toList());
        content.removeAll(); updateButtons.clear(); removeButtons.clear();
        JPanel normal = uiKit.filterPanel("cart.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("cart.state", state, message, null), BorderLayout.NORTH);
        JPanel rows = uiKit.filterPanel("cart.rows", new WrappingGridLayout(280, 12, 12));
        for (CartItemView item : cart.items()) {
            CartItemCard row = new CartItemCard(item, uiKit,
                    () -> navigator.open(new ShopRoute.Product(item.productId())),
                    quantity -> updateQuantity(item.cartItemId(), quantity),
                    () -> confirmRemove(item.cartItemId()),
                    selectedItemIds.contains(item.cartItemId()),
                    selected -> changeSelection(item.cartItemId(), selected));
            JButton update = row.update; JButton remove = row.remove;
            if (queuedKeys.contains("U:" + item.cartItemId())) update.setEnabled(false);
            if (queuedKeys.contains("R:" + item.cartItemId())) remove.setEnabled(false);
            updateButtons.put(item.cartItemId(), update); removeButtons.put(item.cartItemId(), remove);
            rows.add(row);
        }
        JButton selectAll = uiKit.secondaryButton("cart.select-all", "全选");
        selectAll.addActionListener(event -> { selectedItemIds.clear();
            cart.items().forEach(item -> selectedItemIds.add(item.cartItemId()));
            renderCart(ShopPageState.NORMAL, ""); });
        JButton clear = uiKit.secondaryButton("cart.clear-selection", "取消全选");
        clear.addActionListener(event -> { selectedItemIds.clear(); renderCart(ShopPageState.NORMAL, ""); });
        JPanel selectionActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectionActions.add(selectAll); selectionActions.add(clear);
        normal.add(selectionActions, BorderLayout.WEST);
        JButton checkout = uiKit.primaryButton("cart.checkout", "去结算");
        checkout.setEnabled(!selectedItemIds.isEmpty());
        checkout.addActionListener(e -> navigator.open(new ShopRoute.Checkout(selectedItemIds)));
        JLabel total = new JLabel("已选总计：" + CartItemCard.money(selectedTotal()));
        total.setName("cart.selected-total");
        JLabel guidance = new JLabel(selectedItemIds.isEmpty() ? "请至少选择一件商品" : "");
        guidance.setName("cart.selection-guidance");
        JPanel summary = new JPanel(new BorderLayout());
        summary.setName("cart.summary");
        JPanel amounts = new JPanel(new FlowLayout(FlowLayout.LEFT));
        amounts.add(total); amounts.add(guidance);
        summary.add(amounts, BorderLayout.WEST); summary.add(checkout, BorderLayout.EAST);
        normal.add(rows, BorderLayout.CENTER); normal.add(summary, BorderLayout.SOUTH); content.add(normal, BorderLayout.CENTER); refresh();
    }
    private void changeSelection(String id, boolean selected) {
        if (selected) selectedItemIds.add(id); else selectedItemIds.remove(id);
        renderCart(ShopPageState.NORMAL, "");
    }
    private BigDecimal selectedTotal() {
        return cart.items().stream().filter(item -> selectedItemIds.contains(item.cartItemId()))
                .map(item -> item.displayedUnitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private void confirmRemove(String id) {
        if (removePrompt.confirm("确定移除此商品？")) remove(id);
    }
    private CartItemView item(String id) { return cart == null ? null : cart.items().stream().filter(i -> i.cartItemId().equals(id)).findFirst().orElse(null); }
    private void showState(ShopPageState state, String message, Runnable retry) { content.removeAll(); content.add(uiKit.stateView("cart.state", state, message, retry), BorderLayout.CENTER); refresh(); }
    private void refresh() { content.revalidate(); content.repaint(); }
    private record Write(boolean update, String id, int quantity, long generation) {
        private Write(boolean update, String id, int quantity) { this(update, id, quantity, 0); }
        String key() { return (update ? "U:" : "R:") + id; }
    }
}
