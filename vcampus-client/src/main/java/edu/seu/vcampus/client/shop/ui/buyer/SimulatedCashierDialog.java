package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.Objects;
import java.util.EnumMap;

/** Non-blocking simulated payment dialog with retry-safe terminal transitions. */
public final class SimulatedCashierDialog extends JDialog implements CheckoutPanel.ActiveCashier {
    private final ShopClientPort client;
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final CheckoutResult checkout;
    private final Runnable sessionExpired;
    private final Runnable closed;
    private final LatestRequest submissions = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JComboBox<PaymentChannel> channels = new JComboBox<>(PaymentChannel.values());
    private JButton succeeded;
    private final EnumMap<PaymentAttemptStatus, JButton> attemptButtons = new EnumMap<>(PaymentAttemptStatus.class);
    private PaymentAttemptStatus initiatingAttempt;
    private boolean busy;
    private boolean disconnected;
    private boolean disposed;

    public SimulatedCashierDialog(Window owner, ShopClientPort client, ShopNavigator navigator,
            ShopUiKit uiKit, CheckoutResult checkout, Runnable sessionExpired) {
        this(owner, client, navigator, uiKit, checkout, sessionExpired, () -> { });
    }

    SimulatedCashierDialog(Window owner, ShopClientPort client, ShopNavigator navigator,
            ShopUiKit uiKit, CheckoutResult checkout, Runnable sessionExpired, Runnable closed) {
        super(owner, "模拟收银台", ModalityType.MODELESS);
        this.client = Objects.requireNonNull(client, "client");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.checkout = Objects.requireNonNull(checkout, "checkout");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        this.closed = Objects.requireNonNull(closed, "closed");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        add(content); showCashier(ShopPageState.INITIAL, ""); pack();
    }

    public void submit(PaymentChannel channel, PaymentAttemptStatus result) {
        if (busy || disconnected || disposed) return;
        long request = submissions.begin();
        busy = true;
        initiatingAttempt = result;
        showCashier(ShopPageState.SUBMITTING, "正在支付…");
        client.simulatePayment(new SimulatePaymentCommand(checkout.paymentId(), channel, result))
                .whenComplete((payment, failure) -> finish(request, payment, failure));
    }

    public boolean retryEnabled() { return !busy && !disconnected && !disposed; }
    public void disposePage() { dispose(); }
    @Override public boolean isClosed() { return disposed; }
    @Override public void open() { if (!disposed) setVisible(true); }

    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        submissions.dispose();
        super.dispose();
        closed.run();
    }

    private void finish(long request, PaymentView payment, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!submissions.accepts(request)) return;
            busy = false;
            initiatingAttempt = null;
            if (failure != null) {
                String code = ShopUiErrors.code(failure);
                if (ShopUiErrors.sessionExpired(code)) disconnect(code);
                else showCashier(ShopPageState.ERROR, code);
                return;
            }
            if (payment.status() == PaymentStatus.PENDING) { showCashier(ShopPageState.NORMAL, "待支付"); return; }
            disposePage();
            navigator.replaceCurrent(new ShopRoute.PaymentResult(payment));
        });
    }

    private void showCashier(ShopPageState state, String message) {
        content.removeAll();
        content.add(uiKit.stateView("cashier.state", state, message, null), BorderLayout.NORTH);
        JPanel form = uiKit.filterPanel("cashier.form", new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("支付单号：" + checkout.paymentNumber()));
        form.add(new JLabel("金额：¥" + checkout.totalAmount().toPlainString()));
        form.add(new JLabel("到期：" + checkout.expiresAt()));
        form.add(channels);
        succeeded = uiKit.primaryButton("cashier.success", "成功");
        JButton failed = uiKit.secondaryButton("cashier.failed", "失败");
        JButton cancelled = uiKit.secondaryButton("cashier.cancel", "取消");
        attemptButtons.clear();
        attemptButtons.put(PaymentAttemptStatus.SUCCEEDED, succeeded);
        attemptButtons.put(PaymentAttemptStatus.FAILED, failed);
        attemptButtons.put(PaymentAttemptStatus.CANCELLED, cancelled);
        succeeded.addActionListener(event -> submit((PaymentChannel) channels.getSelectedItem(), PaymentAttemptStatus.SUCCEEDED));
        failed.addActionListener(event -> submit((PaymentChannel) channels.getSelectedItem(), PaymentAttemptStatus.FAILED));
        cancelled.addActionListener(event -> submit((PaymentChannel) channels.getSelectedItem(), PaymentAttemptStatus.CANCELLED));
        if (disconnected) {
            channels.setEnabled(false);
            attemptButtons.values().forEach(button -> button.setEnabled(false));
        } else {
            channels.setEnabled(true);
            if (busy && attemptButtons.containsKey(initiatingAttempt)) attemptButtons.get(initiatingAttempt).setEnabled(false);
        }
        form.add(succeeded); form.add(failed); form.add(cancelled);
        content.add(form, BorderLayout.CENTER); content.revalidate(); content.repaint(); pack();
    }

    private void disconnect(String code) {
        if (disconnected) return;
        disconnected = true; submissions.dispose();
        showCashier(ShopPageState.DISCONNECTED, code); sessionExpired.run();
    }
}
