package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopDialogs;
import edu.seu.vcampus.client.shop.ui.CartCountModel;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartItemView;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;

import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;

/** Checkout confirmation page with route-bound price acknowledgement and cashier ownership. */
public final class CheckoutPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final ShopDialogs dialogs;
    private final Runnable sessionExpired;
    private final CashierFactory cashierFactory;
    private final LatestRequest loads = new LatestRequest();
    private final LatestRequest submissions = new LatestRequest();
    private final LatestRequest cartRefreshes = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private CartCountModel cartCount = new CartCountModel();
    private CartView sourceCart;
    private CartView cart;
    private CheckoutResult checkout;
    private long activeLoad;
    private boolean disposed;
    private boolean disconnected;
    private boolean checkoutInFlight;
    private ActiveCashier cashier;
    private long loadCartRevision;

    public CheckoutPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            ShopDialogs dialogs, Runnable sessionExpired) {
        this(client, navigator, uiKit, dialogs, sessionExpired,
                (owner, cashierClient, cashierNavigator, cashierKit, checkout,
                        cashierExpired, terminal, settled, closed) -> new SimulatedCashierDialog(owner,
                        cashierClient, cashierNavigator, cashierKit, checkout, cashierExpired,
                        terminal, settled, closed));
    }

    CheckoutPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            ShopDialogs dialogs, Runnable sessionExpired, CashierFactory cashierFactory) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.cashierFactory = Objects.requireNonNull(cashierFactory, "cashierFactory");
        navigator.addListener(this::routeChanged);
        add(content, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load() {
        if (disposed || disconnected) return;
        long request = loads.begin();
        activeLoad = request;
        submissions.begin();
        checkoutInFlight = false;
        checkout = null;
        long cartRevision = cartCount.beginUpdate();
        loadCartRevision = cartRevision;
        showState(ShopPageState.LOADING, "加载中…", null);
        client.getCart().whenComplete((result, failure) ->
                finishLoad(request, cartRevision, result, failure));
    }

    public List<CartItemView> visibleItems() { return cart == null ? List.of() : cart.items(); }
    public CheckoutResult currentCheckout() { return checkout; }
    public void setCartCountModel(CartCountModel cartCount) {
        this.cartCount = Objects.requireNonNull(cartCount, "cartCount");
    }
    public void submit() { submit(cart, activeLoad, false); }
    public void confirmLatestPriceAndRetry() { submit(cart, activeLoad, true); }

    public void disposePage() {
        if (disposed) return;
        disposed = true;
        loads.dispose(); submissions.dispose(); cartRefreshes.dispose();
        if (cashier != null) cashier.disposePage();
        cashier = null;
    }
    public void dispose() { disposePage(); }

    private void submit(CartView snapshot, long loadAtSubmit, boolean acceptLatestPrice) {
        if (disposed || disconnected || snapshot == null || snapshot.items().isEmpty() || checkoutInFlight
                || activeLoad != loadAtSubmit || cart != snapshot || activeCashier()) return;
        long request = submissions.begin();
        checkoutInFlight = true;
        showCheckout(ShopPageState.SUBMITTING, "正在提交订单…");
        client.checkout(command(snapshot, acceptLatestPrice))
                .whenComplete((result, failure) -> finishCheckout(request, loadAtSubmit, snapshot, result, failure));
    }

    private CheckoutCommand command(CartView snapshot, boolean acceptLatestPrice) {
        return new CheckoutCommand(snapshot.items().stream().map(item -> new CheckoutItem(
                item.cartItemId(), item.displayedUnitPrice())).toList(), acceptLatestPrice);
    }

    private void finishLoad(long request, long cartRevision, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || !loads.accepts(request)) return;
            if (failure != null) { showFailure(failure, this::load); return; }
            sourceCart = result;
            cart = selectedCart(result);
            cartCount.update(cartRevision, result);
            if (selectionMissing(result)) showState(ShopPageState.EMPTY,
                    "所选商品已变化，请返回购物车重新选择", this::load);
            else if (cart.items().isEmpty()) showState(ShopPageState.EMPTY, "购物车为空", this::load);
            else showCheckout(ShopPageState.NORMAL, "");
        });
    }

    private void finishCheckout(long request, long loadAtSubmit, CartView snapshot,
            CheckoutResult result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || !submissions.accepts(request) || activeLoad != loadAtSubmit || cart != snapshot) return;
            checkoutInFlight = false;
            if (failure == null) {
                checkout = result;
                publishRemainingCartCount(snapshot);
                refreshCartCount();
                showCheckout(ShopPageState.NORMAL, "");
                openCashier(result);
                return;
            }
            String code = ShopUiErrors.code(failure);
            if (ShopUiErrors.sessionExpired(code)) {
                disconnect(code);
            } else if ("SHOP_PRICE_CHANGED".equals(code)) {
                showCheckout(ShopPageState.ERROR, ShopUiErrors.message(code));
                dialogs.confirm(code, () -> submit(snapshot, loadAtSubmit, true));
            } else {
                showCheckout(ShopPageState.ERROR, ShopUiErrors.message(code));
                dialogs.showError(code);
            }
        });
    }

    private boolean activeCashier() { return cashier != null && !cashier.isClosed(); }
    private void openCashier(CheckoutResult result) {
        if (disposed || activeCashier()) return;
        ActiveCashier[] holder = new ActiveCashier[1];
        Runnable closed = () -> {
            if (cashier == holder[0]) {
                cashier = null;
                refreshCartCount();
                if (!disposed && cart != null
                        && navigator.current().orElse(null) instanceof ShopRoute.Checkout) {
                    showCheckout(ShopPageState.NORMAL, "");
                }
            }
        };
        Consumer<PaymentView> terminal = payment -> {
            if (disposed || cashier != holder[0] || holder[0].isClosed()
                    || payment.status() == PaymentStatus.PENDING) return;
            navigator.completeCheckout(new ShopRoute.PaymentResult(payment));
        };
        holder[0] = cashierFactory.create(SwingUtilities.getWindowAncestor(this), client, navigator,
                uiKit, result, sessionExpired, terminal, this::refreshCartCount, closed);
        cashier = holder[0];
        showCheckout(ShopPageState.NORMAL, "");
        if (isShowing()) cashier.open();
    }

    private void showCheckout(ShopPageState state, String message) {
        if (cart == null) { showState(state, message, this::load); return; }
        content.removeAll();
        JPanel normal = uiKit.filterPanel("checkout.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("checkout.state", state, message, null), BorderLayout.NORTH);
        JPanel items = uiKit.filterPanel("checkout.items", new FlowLayout(FlowLayout.LEFT));
        items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        for (CartItemView item : cart.items()) items.add(new CheckoutItemRow(item,
                () -> navigator.open(new ShopRoute.Product(item.productId()))));
        JButton submit = uiKit.primaryButton("checkout.submit", "提交订单");
        submit.addActionListener(event -> submit());
        submit.setEnabled(!checkoutInFlight && !activeCashier() && state != ShopPageState.SUBMITTING);
        JLabel total = new JLabel("总计：" + CartItemCard.money(cart.displayedTotal())); total.setName("checkout.total");
        JPanel summary = new JPanel(new BorderLayout()); summary.add(total, BorderLayout.WEST); summary.add(submit, BorderLayout.EAST);
        normal.add(items, BorderLayout.CENTER); normal.add(summary, BorderLayout.SOUTH);
        content.add(normal, BorderLayout.CENTER); refresh();
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) disconnect(code);
        else showState(ShopPageState.ERROR, ShopUiErrors.message(code), retry);
    }
    private void disconnect(String code) {
        if (disconnected) return;
        disconnected = true; checkoutInFlight = false;
        loads.dispose(); submissions.dispose(); cartRefreshes.dispose();
        showState(ShopPageState.DISCONNECTED, ShopUiErrors.message(code), null);
        sessionExpired.run();
    }

    private CartView selectedCart(CartView source) {
        Set<String> selected = routeSelection();
        if (selected.isEmpty()) return source;
        List<CartItemView> items = source.items().stream()
                .filter(item -> selected.contains(item.cartItemId())).toList();
        if (items.size() != selected.size()) return new CartView(source.cartId(), List.of(),
                java.math.BigDecimal.ZERO);
        java.math.BigDecimal total = items.stream()
                .map(item -> item.displayedUnitPrice().multiply(
                        java.math.BigDecimal.valueOf(item.quantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return new CartView(source.cartId(), items, total);
    }

    private boolean selectionMissing(CartView source) {
        Set<String> selected = routeSelection();
        return !selected.isEmpty() && source.items().stream()
                .filter(item -> selected.contains(item.cartItemId())).count() != selected.size();
    }

    private Set<String> routeSelection() {
        return navigator.current()
                .filter(ShopRoute.Checkout.class::isInstance)
                .map(ShopRoute.Checkout.class::cast)
                .map(ShopRoute.Checkout::cartItemIds)
                .orElse(Set.of());
    }

    private void publishRemainingCartCount(CartView submitted) {
        if (sourceCart == null) return;
        Set<String> submittedIds = submitted.items().stream()
                .map(CartItemView::cartItemId).collect(java.util.stream.Collectors.toSet());
        List<CartItemView> remaining = sourceCart.items().stream()
                .filter(item -> !submittedIds.contains(item.cartItemId())).toList();
        java.math.BigDecimal total = remaining.stream()
                .map(item -> item.displayedUnitPrice().multiply(
                        java.math.BigDecimal.valueOf(item.quantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        cartCount.update(new CartView(sourceCart.cartId(), remaining, total));
    }

    private void routeChanged(ShopRoute route) {
        if (route instanceof ShopRoute.Checkout) return;
        loads.begin();
        submissions.begin();
        cartCount.cancel(loadCartRevision);
        loadCartRevision = 0;
        checkoutInFlight = false;
        ActiveCashier active = cashier;
        if (active != null) {
            active.disposePage();
            if (active.isClosed() && cashier == active) {
                cashier = null;
            }
        }
    }

    private void refreshCartCount() {
        if (disposed || disconnected) return;
        long request = cartRefreshes.begin();
        long cartRevision = cartCount.beginUpdate();
        java.util.concurrent.CompletableFuture<CartView> response = client.getCart();
        if (response == null) {
            cartCount.cancel(cartRevision);
            return;
        }
        response.whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (disposed || !cartRefreshes.accepts(request)) return;
            if (failure == null) {
                cartCount.update(cartRevision, result);
            } else {
                cartCount.cancel(cartRevision);
                String code = ShopUiErrors.code(failure);
                if (ShopUiErrors.sessionExpired(code)) disconnect(code);
            }
        }));
    }
    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("checkout.state", state, message, retry), BorderLayout.CENTER); refresh();
    }
    private void refresh() { content.revalidate(); content.repaint(); }

    @FunctionalInterface interface CashierFactory {
        ActiveCashier create(Window owner, ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
                CheckoutResult checkout, Runnable sessionExpired, Consumer<PaymentView> terminal,
                Runnable settled, Runnable closed);
    }
    interface ActiveCashier {
        void open();
        void disposePage();
        boolean isClosed();
    }
}
