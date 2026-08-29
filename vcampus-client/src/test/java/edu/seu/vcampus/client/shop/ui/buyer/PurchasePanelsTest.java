package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ShopClientFixtures;
import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientException;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopDialogs;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.LayoutManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchasePanelsTest {
    @Test
    void cartUsesItemIdentityAndRowVersionAndRendersReturnedCart() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CartView initial = ShopClientFixtures.cartView();
        CartView updated = new CartView("cart-1", List.of(), BigDecimal.ZERO);
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(initial));
        when(client.updateCartItem(any())).thenReturn(CompletableFuture.completedFuture(updated));
        when(client.removeCartItem("cart-item-1")).thenReturn(CompletableFuture.completedFuture(updated));
        CartPanel panel = onEdt(() -> new CartPanel(client, new ShopNavigator(route -> { }),
                new RecordingKit(), () -> { }));

        onEdt(panel::load);
        flushEdt();
        onEdt(() -> panel.updateQuantity("cart-item-1", 4));
        flushEdt();
        verify(client).updateCartItem(new UpdateCartItemCommand("cart-item-1", 4, 0));
        assertThat(panel.visibleItems()).isEmpty();

        onEdt(panel::load);
        flushEdt();
        onEdt(() -> panel.remove("cart-item-1"));
        flushEdt();
        verify(client).removeCartItem("cart-item-1");
    }

    @Test
    void checkoutUsesEveryDisplayedCartPriceAndNavigatesToCashier() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CartView cart = ShopClientFixtures.cartView();
        CheckoutResult checkout = ShopClientFixtures.checkoutResult();
        List<ShopRoute> routes = new ArrayList<>();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(cart));
        when(client.checkout(any())).thenReturn(CompletableFuture.completedFuture(checkout));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(routes::add),
                new RecordingKit(), new RecordingDialogs(), () -> { }));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);
        flushEdt();

        verify(client).checkout(new CheckoutCommand(List.of(
                new CheckoutItem("cart-item-1", new BigDecimal("3.00"))), false));
        assertThat(panel.currentCheckout()).isEqualTo(checkout);
    }

    @Test
    void emptyCartMountsEmptyStateAndDoesNotCheckout() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingKit kit = new RecordingKit();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(
                new CartView("cart", List.of(), BigDecimal.ZERO)));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(route -> { }),
                kit, new RecordingDialogs(), () -> { }));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);

        assertThat(kit.states).contains(ShopPageState.EMPTY);
        verify(client, never()).checkout(any());
    }

    @Test
    void priceChangeKeepsCartAndRetriesOnlyAfterConfirmation() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CartView cart = ShopClientFixtures.cartView();
        RecordingDialogs dialogs = new RecordingDialogs();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(cart));
        when(client.checkout(any()))
                .thenReturn(CompletableFuture.failedFuture(new ShopClientException("SHOP_PRICE_CHANGED")))
                .thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(route -> { }),
                new RecordingKit(), dialogs, () -> { }));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);
        flushEdt();

        assertThat(panel.visibleItems()).hasSize(cart.items().size());
        assertThat(dialogs.confirmedCode()).isEqualTo("SHOP_PRICE_CHANGED");
        onEdt(dialogs::acceptLatest);
        flushEdt();
        verify(client).checkout(new CheckoutCommand(List.of(
                new CheckoutItem("cart-item-1", new BigDecimal("3.00"))), true));
    }

    @Test
    void stockFailureRetainsCartAndShowsStableError() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingDialogs dialogs = new RecordingDialogs();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.cartView()));
        when(client.checkout(any())).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("SHOP_INSUFFICIENT_STOCK")));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(route -> { }),
                new RecordingKit(), dialogs, () -> { }));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);
        flushEdt();

        assertThat(panel.visibleItems()).hasSize(1);
        assertThat(dialogs.errorCodes).containsExactly("SHOP_INSUFFICIENT_STOCK");
    }

    @Test
    void workerAndStaleCompletionOnlyPublishLatestUndisposedCartOnEdt() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<CartView> first = new CompletableFuture<>();
        CompletableFuture<CartView> second = new CompletableFuture<>();
        RecordingKit kit = new RecordingKit();
        when(client.getCart()).thenReturn(first, second);
        CartPanel panel = onEdt(() -> new CartPanel(client, new ShopNavigator(route -> { }), kit, () -> { }));

        onEdt(panel::load);
        onEdt(panel::load);
        Thread worker = new Thread(() -> {
            first.complete(ShopClientFixtures.cartView());
            second.complete(new CartView("cart", List.of(), BigDecimal.ZERO));
        });
        worker.start();
        worker.join();
        flushEdt();

        assertThat(panel.visibleItems()).isEmpty();
        assertThat(kit.uiThreadCalls).allMatch(Boolean::booleanValue);
        onEdt(panel::disposePage);
        verify(client, org.mockito.Mockito.times(2)).getCart();
    }

    @Test
    void cartDisablesOnlyInitiatingControlAndRestoresItAfterStableWriteFailure() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<CartView> update = new CompletableFuture<>();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.cartView()));
        when(client.updateCartItem(any())).thenReturn(update);
        CartPanel panel = onEdt(() -> new CartPanel(client, new ShopNavigator(route -> { }),
                new RecordingKit(), () -> { }));

        onEdt(panel::load);
        flushEdt();
        onEdt(() -> panel.updateQuantity("cart-item-1", 3));
        assertThat(component(panel, "cart.update-cart-item-1", JButton.class).isEnabled()).isFalse();
        assertThat(component(panel, "cart.remove-cart-item-1", JButton.class).isEnabled()).isTrue();
        update.completeExceptionally(new ShopClientException("SHOP_UNAVAILABLE"));
        flushEdt();

        assertThat(panel.visibleItems()).hasSize(1);
        assertThat(component(panel, "cart.update-cart-item-1", JButton.class).isEnabled()).isTrue();
    }

    @Test
    void disposedCheckoutIgnoresWorkerLoadCompletion() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<CartView> load = new CompletableFuture<>();
        RecordingKit kit = new RecordingKit();
        when(client.getCart()).thenReturn(load);
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(route -> { }),
                kit, new RecordingDialogs(), () -> { }));

        onEdt(panel::load);
        onEdt(panel::disposePage);
        Thread worker = new Thread(() -> load.complete(ShopClientFixtures.cartView()));
        worker.start();
        worker.join();
        flushEdt();

        assertThat(panel.visibleItems()).isEmpty();
        assertThat(kit.states).doesNotContain(ShopPageState.NORMAL);
    }

    @Test
    void failedPaymentRemainsRetryableAndSuccessNavigatesToResult() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        PaymentView pending = payment(PaymentStatus.PENDING, null);
        PaymentView success = payment(PaymentStatus.SUCCEEDED, PaymentChannel.ALIPAY);
        List<ShopRoute> routes = new ArrayList<>();
        when(client.simulatePayment(any())).thenReturn(CompletableFuture.completedFuture(pending),
                CompletableFuture.completedFuture(success));
        SimulatedCashierDialog cashier = onEdt(() -> new SimulatedCashierDialog(null, client,
                new ShopNavigator(routes::add), new RecordingKit(), ShopClientFixtures.checkoutResult(), () -> { }));

        onEdt(() -> cashier.submit(PaymentChannel.ALIPAY, PaymentAttemptStatus.FAILED));
        flushEdt();
        assertThat(cashier.retryEnabled()).isTrue();
        onEdt(() -> cashier.submit(PaymentChannel.ALIPAY, PaymentAttemptStatus.SUCCEEDED));
        flushEdt();

        assertThat(routes).containsExactly(new ShopRoute.PaymentResult(success));
    }

    @Test
    void terminalCancelledPaymentNavigatesAndPendingDoesNotCloseStaleDialog() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PaymentView> pending = new CompletableFuture<>();
        CompletableFuture<PaymentView> cancelled = new CompletableFuture<>();
        List<ShopRoute> routes = new ArrayList<>();
        when(client.simulatePayment(any())).thenReturn(pending, cancelled);
        SimulatedCashierDialog cashier = onEdt(() -> new SimulatedCashierDialog(null, client,
                new ShopNavigator(routes::add), new RecordingKit(), ShopClientFixtures.checkoutResult(), () -> { }));

        onEdt(() -> cashier.submit(PaymentChannel.WECHAT, PaymentAttemptStatus.FAILED));
        pending.complete(payment(PaymentStatus.PENDING, null));
        flushEdt();
        onEdt(() -> cashier.submit(PaymentChannel.WECHAT, PaymentAttemptStatus.CANCELLED));
        cancelled.complete(payment(PaymentStatus.CANCELLED, null));
        flushEdt();

        assertThat(routes).containsExactly(new ShopRoute.PaymentResult(payment(PaymentStatus.CANCELLED, null)));
    }

    @Test
    void resultOffersHomeAndEmptyCartNavigation() throws Exception {
        List<ShopRoute> routes = new ArrayList<>();
        PaymentResultPanel panel = onEdt(() -> new PaymentResultPanel(new ShopNavigator(routes::add),
                new RecordingKit(), payment(PaymentStatus.SUCCEEDED, PaymentChannel.BANK_CARD)));

        onEdt(panel::openHome);
        onEdt(panel::openCart);

        assertThat(routes).hasSize(2);
        assertThat(routes.get(1)).isEqualTo(new ShopRoute.Cart());
        assertThat(component(panel, "payment-number", JLabel.class).getText()).isEqualTo("P0001");
    }

    @Test
    void stalePriceConfirmationCannotSubmitAReplacementCart() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingDialogs dialogs = new RecordingDialogs();
        CartView firstCart = ShopClientFixtures.cartView();
        CartView replacementCart = new CartView("cart-2", List.of(), BigDecimal.ZERO);
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(firstCart),
                CompletableFuture.completedFuture(replacementCart));
        when(client.checkout(any())).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("SHOP_PRICE_CHANGED")));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(route -> { }),
                new RecordingKit(), dialogs, () -> { }, RecordingCashier::new));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);
        flushEdt();
        onEdt(panel::load);
        flushEdt();
        onEdt(dialogs::acceptLatest);

        verify(client, org.mockito.Mockito.times(1)).checkout(any());
    }

    @Test
    void activeCashierBlocksCheckoutUntilItsCloseCallbackUnbindsIt() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingCashierFactory factory = new RecordingCashierFactory();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.cartView()));
        when(client.checkout(any())).thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()),
                CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, new ShopNavigator(route -> { }),
                new RecordingKit(), new RecordingDialogs(), () -> { }, factory));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);
        flushEdt();
        onEdt(panel::submit);
        verify(client, org.mockito.Mockito.times(1)).checkout(any());
        assertThat(factory.created).hasSize(1);
        onEdt(factory.created.getFirst()::close);
        onEdt(panel::submit);
        flushEdt();

        verify(client, org.mockito.Mockito.times(2)).checkout(any());
    }

    private static PaymentView payment(PaymentStatus status, PaymentChannel channel) {
        return new PaymentView("payment-1", "group-1", "P0001", new BigDecimal("6.00"), status,
                channel, Instant.parse("2026-08-29T00:15:00Z"), null, 0);
    }

    private static final class RecordingDialogs implements ShopDialogs {
        private final List<String> errorCodes = new ArrayList<>();
        private String confirmationCode;
        private Runnable accepted;

        @Override public void showError(String code) { errorCodes.add(code); }
        @Override public void confirm(String code, Runnable accepted) {
            confirmationCode = code;
            this.accepted = accepted;
        }
        String confirmedCode() { return confirmationCode; }
        void acceptLatest() { accepted.run(); }
    }

    private static final class RecordingKit implements ShopUiKit {
        private final EnumSet<ShopPageState> states = EnumSet.noneOf(ShopPageState.class);
        private final List<Boolean> uiThreadCalls = new ArrayList<>();

        @Override public JButton primaryButton(String name, String text) { return named(new JButton(text), name); }
        @Override public JButton secondaryButton(String name, String text) { return named(new JButton(text), name); }
        @Override public JPanel filterPanel(String name, LayoutManager layout) { return named(new JPanel(layout), name); }
        @Override public JPanel productCard(String name, LayoutManager layout) { return named(new JPanel(layout), name); }
        @Override public JComponent stateView(String name, ShopPageState state, String message, Runnable retry) {
            states.add(state); uiThreadCalls.add(javax.swing.SwingUtilities.isEventDispatchThread());
            return named(new JLabel(message), name);
        }
        private static <T extends JComponent> T named(T component, String name) {
            component.setName(name); return component;
        }
    }

    private static final class RecordingCashierFactory implements CheckoutPanel.CashierFactory {
        private final List<RecordingCashier> created = new ArrayList<>();

        @Override
        public CheckoutPanel.ActiveCashier create(java.awt.Window owner, ShopClientPort client,
                ShopNavigator navigator, ShopUiKit uiKit, CheckoutResult checkout,
                Runnable sessionExpired, Runnable closed) {
            RecordingCashier cashier = new RecordingCashier(owner, client, navigator, uiKit, checkout,
                    sessionExpired, closed);
            created.add(cashier);
            return cashier;
        }
    }

    private static final class RecordingCashier implements CheckoutPanel.ActiveCashier {
        private final Runnable closed;
        private boolean closedState;

        private RecordingCashier(java.awt.Window owner, ShopClientPort client, ShopNavigator navigator,
                ShopUiKit uiKit, CheckoutResult checkout, Runnable sessionExpired, Runnable closed) {
            this.closed = closed;
        }
        @Override public void open() { }
        @Override public void disposePage() { close(); }
        @Override public boolean isClosed() { return closedState; }
        void close() { if (!closedState) { closedState = true; closed.run(); } }
    }
}
