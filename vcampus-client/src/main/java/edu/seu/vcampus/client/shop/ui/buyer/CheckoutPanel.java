package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopDialogs;
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
import java.util.List;
import java.util.Objects;

/** Checkout confirmation page; price acknowledgement is an explicit second request. */
public final class CheckoutPanel extends JPanel {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final ShopDialogs dialogs;
    private final Runnable sessionExpired;
    private final LatestRequest loads = new LatestRequest();
    private final LatestRequest submissions = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private CartView cart;
    private CheckoutResult checkout;
    private long displayedLoad;
    private JButton submit;
    private SimulatedCashierDialog cashier;

    public CheckoutPanel(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            ShopDialogs dialogs, Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        add(content, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load() {
        long request = loads.begin();
        submissions.begin();
        checkout = null;
        showState(ShopPageState.LOADING, "加载中…", null);
        client.getCart().whenComplete((result, failure) -> finishLoad(request, result, failure));
    }

    public List<CartItemView> visibleItems() { return cart == null ? List.of() : cart.items(); }
    public CheckoutResult currentCheckout() { return checkout; }

    public void submit() { submit(false); }

    public void confirmLatestPriceAndRetry() { submit(true); }

    public void disposePage() {
        loads.dispose(); submissions.dispose();
        if (cashier != null) cashier.disposePage();
    }
    public void dispose() { disposePage(); }

    private void submit(boolean acceptLatestPrice) {
        if (cart == null || cart.items().isEmpty() || submit == null || !submit.isEnabled()) return;
        long request = submissions.begin();
        long loadAtSubmission = displayedLoad;
        submit.setEnabled(false);
        showCheckout(ShopPageState.SUBMITTING, "正在提交订单…");
        client.checkout(command(acceptLatestPrice))
                .whenComplete((result, failure) -> finishCheckout(request, loadAtSubmission, result, failure));
    }

    private CheckoutCommand command(boolean acceptLatestPrice) {
        return new CheckoutCommand(cart.items().stream().map(item -> new CheckoutItem(
                item.cartItemId(), item.displayedUnitPrice())).toList(), acceptLatestPrice);
    }

    private void finishLoad(long request, CartView result, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!loads.accepts(request)) return;
            if (failure != null) { showFailure(failure, this::load); return; }
            displayedLoad = request;
            cart = result;
            if (cart.items().isEmpty()) showState(ShopPageState.EMPTY, "购物车为空", this::load);
            else showCheckout(ShopPageState.NORMAL, "");
        });
    }

    private void finishCheckout(long request, long loadAtSubmission, CheckoutResult result,
            Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!submissions.accepts(request) || displayedLoad != loadAtSubmission) return;
            if (failure == null) {
                checkout = result;
                showCheckout(ShopPageState.NORMAL, "");
                openCashier(result);
                return;
            }
            String code = ShopUiErrors.code(failure);
            if (ShopUiErrors.sessionExpired(code)) {
                showState(ShopPageState.DISCONNECTED, code, this::load);
                sessionExpired.run();
            } else if ("SHOP_PRICE_CHANGED".equals(code)) {
                showCheckout(ShopPageState.ERROR, code);
                dialogs.confirm(code, this::confirmLatestPriceAndRetry);
            } else {
                showCheckout(ShopPageState.ERROR, code);
                dialogs.showError(code);
            }
        });
    }

    private void openCashier(CheckoutResult result) {
        cashier = new SimulatedCashierDialog(SwingUtilities.getWindowAncestor(this), client,
                navigator, uiKit, result, sessionExpired);
        if (isShowing()) cashier.setVisible(true);
    }

    private void showCheckout(ShopPageState state, String message) {
        if (cart == null) { showState(state, message, this::load); return; }
        content.removeAll();
        JPanel normal = uiKit.filterPanel("checkout.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("checkout.state", state, message, null), BorderLayout.NORTH);
        JPanel items = uiKit.filterPanel("checkout.items", new FlowLayout(FlowLayout.LEFT));
        for (CartItemView item : cart.items()) items.add(new JLabel("%s | ¥%s".formatted(
                item.productName(), item.displayedUnitPrice().toPlainString())));
        submit = uiKit.primaryButton("checkout.submit", "提交订单");
        submit.addActionListener(event -> submit());
        if (state == ShopPageState.SUBMITTING) submit.setEnabled(false);
        normal.add(items, BorderLayout.CENTER); normal.add(submit, BorderLayout.SOUTH);
        content.add(normal, BorderLayout.CENTER); refresh();
    }

    private void showFailure(Throwable failure, Runnable retry) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) { showState(ShopPageState.DISCONNECTED, code, retry); sessionExpired.run(); }
        else showState(ShopPageState.ERROR, code, retry);
    }
    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll(); content.add(uiKit.stateView("checkout.state", state, message, retry), BorderLayout.CENTER); refresh();
    }
    private void refresh() { content.revalidate(); content.repaint(); }
}
