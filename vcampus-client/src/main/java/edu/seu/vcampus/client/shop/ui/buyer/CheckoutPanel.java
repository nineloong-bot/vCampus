package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopDialogs;
import edu.seu.vcampus.client.shop.ui.CartCountModel;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartItemView;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import java.util.Objects;

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
    private final JPanel content = new JPanel(new BorderLayout());
    private CartCountModel cartCount = new CartCountModel();
    private CartView cart;
    private CheckoutResult checkout;
    private long activeLoad;
    private boolean disposed;
    private boolean disconnected;
    private boolean checkoutInFlight;
    private ActiveCashier cashier;

    public CheckoutPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            ShopDialogs dialogs, Runnable sessionExpired) {
        this(client, navigator, uiKit, dialogs, sessionExpired, SimulatedCashierDialog::new);
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
        showState(ShopPageState.LOADING, "加载中…", null);
        client.getCart().whenComplete((result, failure) -> finishLoad(request, result, failure));
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
        loads.dispose(); submissions.dispose();
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

    private void finishLoad(long request, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (disposed || !loads.accepts(request)) return;
            if (failure != null) { showFailure(failure, this::load); return; }
            cart = result;
            cartCount.update(result);
            if (cart.items().isEmpty()) showState(ShopPageState.EMPTY, "购物车为空", this::load);
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
                showCheckout(ShopPageState.NORMAL, "");
                openCashier(result);
                return;
            }
            String code = ShopUiErrors.code(failure);
            if (ShopUiErrors.sessionExpired(code)) {
                disconnect(code);
            } else if ("SHOP_PRICE_CHANGED".equals(code)) {
                showCheckout(ShopPageState.ERROR, code);
                dialogs.confirm(code, () -> submit(snapshot, loadAtSubmit, true));
            } else {
                showCheckout(ShopPageState.ERROR, code);
                dialogs.showError(code);
            }
        });
    }

    private boolean activeCashier() { return cashier != null && !cashier.isClosed(); }
    private void openCashier(CheckoutResult result) {
        if (disposed || activeCashier()) return;
        ActiveCashier[] holder = new ActiveCashier[1];
        Runnable closed = () -> {
            if (cashier == holder[0]) { cashier = null; if (!disposed && cart != null) showCheckout(ShopPageState.NORMAL, ""); }
        };
        holder[0] = cashierFactory.create(SwingUtilities.getWindowAncestor(this), client, navigator,
                uiKit, result, sessionExpired, closed);
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
        for (CartItemView item : cart.items()) items.add(new JLabel("%s | ¥%s".formatted(
                item.productName(), item.displayedUnitPrice().toPlainString())));
        JButton submit = uiKit.primaryButton("checkout.submit", "提交订单");
        submit.addActionListener(event -> submit());
        submit.setEnabled(!checkoutInFlight && !activeCashier() && state != ShopPageState.SUBMITTING);
        normal.add(items, BorderLayout.CENTER); normal.add(submit, BorderLayout.SOUTH);
        content.add(normal, BorderLayout.CENTER); refresh();
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) disconnect(code);
        else showState(ShopPageState.ERROR, code, retry);
    }
    private void disconnect(String code) {
        if (disconnected) return;
        disconnected = true; checkoutInFlight = false;
        loads.dispose(); submissions.dispose();
        showState(ShopPageState.DISCONNECTED, code, null); sessionExpired.run();
    }
    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("checkout.state", state, message, retry), BorderLayout.CENTER); refresh();
    }
    private void refresh() { content.revalidate(); content.repaint(); }

    @FunctionalInterface interface CashierFactory {
        ActiveCashier create(Window owner, ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
                CheckoutResult checkout, Runnable sessionExpired, Runnable closed);
    }
    interface ActiveCashier {
        void open();
        void disposePage();
        boolean isClosed();
    }
}
