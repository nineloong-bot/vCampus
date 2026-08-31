package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopDialogs;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.PaymentView;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Test-only bridge for exercising CheckoutPanel's package-private cashier factory. */
public final class CheckoutPanelTestSeam {
    private CheckoutPanelTestSeam() { }

    public static Fixture create(ShopClientPort client, ShopNavigator navigator, ShopUiKit uiKit,
            Runnable sessionExpired) {
        AtomicInteger cashierDisposals = new AtomicInteger();
        AtomicReference<Runnable> cashierSessionExpired = new AtomicReference<>();
        AtomicReference<Consumer<PaymentView>> terminalPayment = new AtomicReference<>();
        CheckoutPanel panel = new CheckoutPanel(client, navigator, uiKit, new NoopDialogs(),
                sessionExpired, (owner, cashierClient, cashierNavigator, cashierKit, checkout,
                        cashierExpired, terminal, settled, closed) -> {
                    cashierSessionExpired.set(cashierExpired);
                    terminalPayment.set(terminal);
                    return new RecordingCashier(cashierDisposals, closed);
                });
        return new Fixture(panel, cashierDisposals, cashierSessionExpired, terminalPayment);
    }

    public record Fixture(CheckoutPanel panel, AtomicInteger cashierDisposals,
            AtomicReference<Runnable> cashierSessionExpired,
            AtomicReference<Consumer<PaymentView>> terminalPayment) {
        public void complete(PaymentView payment) { terminalPayment.get().accept(payment); }
    }

    private static final class NoopDialogs implements ShopDialogs {
        @Override public void showError(String code) { }
        @Override public void confirm(String code, Runnable accepted) { }
    }

    private static final class RecordingCashier implements CheckoutPanel.ActiveCashier {
        private final AtomicInteger disposals;
        private final Runnable closed;
        private boolean closedState;

        private RecordingCashier(AtomicInteger disposals, Runnable closed) {
            this.disposals = disposals;
            this.closed = closed;
        }

        @Override public void open() { }
        @Override public boolean isClosed() { return closedState; }
        @Override public void disposePage() {
            if (!closedState) {
                closedState = true;
                disposals.incrementAndGet();
                closed.run();
            }
        }
    }
}
