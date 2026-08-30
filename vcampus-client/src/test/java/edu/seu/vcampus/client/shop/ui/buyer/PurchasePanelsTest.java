package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ShopClientFixtures;
import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientException;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopDialogs;
import edu.seu.vcampus.client.shop.ui.CartCountModel;
import edu.seu.vcampus.client.shop.ui.ShopToolbar;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartItemView;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutItem;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentAttemptStatus;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
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
import static org.mockito.Mockito.times;
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
    void resultOffersHomeAndPaidOrdersNavigation() throws Exception {
        List<ShopRoute> routes = new ArrayList<>();
        PaymentResultPanel panel = onEdt(() -> new PaymentResultPanel(new ShopNavigator(routes::add),
                new RecordingKit(), payment(PaymentStatus.SUCCEEDED, PaymentChannel.BANK_CARD)));

        onEdt(panel::openHome);
        onEdt(panel::openPaidOrders);

        assertThat(routes).hasSize(2);
        assertThat(routes.get(1)).isEqualTo(new ShopRoute.My());
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

    @Test
    void leavingCheckoutClosesCashierAndRejectsItsLaterTerminalPayment() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingCashierFactory factory = new RecordingCashierFactory();
        ShopNavigator navigator = new ShopNavigator(route -> { });
        ShopToolbar toolbar = onEdt(() -> new ShopToolbar(
                navigator, new CartCountModel(), new RecordingKit()));
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.cartView()));
        when(client.checkout(any())).thenReturn(
                CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, navigator,
                new RecordingKit(), new RecordingDialogs(), () -> { }, factory));

        onEdt(() -> {
            navigator.open(new ShopRoute.Home(new HomeProductQuery(null, null,
                    ProductSortMode.SALES_DESC, 0, 20)));
            navigator.open(new ShopRoute.Checkout());
            panel.load();
        });
        flushEdt();
        onEdt(panel::submit);
        flushEdt();
        RecordingCashier cashier = factory.created.getFirst();

        onEdt(() -> component(toolbar, "shop.my", JButton.class).doClick());
        assertThat(cashier.isClosed()).isTrue();
        onEdt(() -> component(toolbar, "shop.back", JButton.class).doClick());
        onEdt(() -> cashier.complete(payment(PaymentStatus.SUCCEEDED, PaymentChannel.ALIPAY)));

        assertThat(navigator.current()).contains(new ShopRoute.Checkout());
        assertThat(navigator.history()).containsExactly(new ShopRoute.Home(
                new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20)));
    }

    @Test
    void acceptedSuccessfulCashierTerminalClearsCountAndReplacesCheckout() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingCashierFactory factory = new RecordingCashierFactory();
        ShopNavigator navigator = new ShopNavigator(route -> { });
        CartCountModel count = new CartCountModel();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.cartView()));
        when(client.checkout(any())).thenReturn(
                CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client, navigator,
                new RecordingKit(), new RecordingDialogs(), () -> { }, factory));
        onEdt(() -> panel.setCartCountModel(count));

        onEdt(() -> {
            navigator.open(new ShopRoute.Checkout());
            panel.load();
        });
        flushEdt();
        onEdt(panel::submit);
        flushEdt();
        PaymentView success = payment(PaymentStatus.SUCCEEDED, PaymentChannel.ALIPAY);
        onEdt(() -> factory.created.getFirst().complete(success));

        assertThat(count.totalQuantity()).isZero();
        assertThat(navigator.current()).contains(new ShopRoute.PaymentResult(success));
        assertThat(navigator.history()).isEmpty();
    }

    @Test
    void queuedCartWritesDisableBothInitiatorsAndRunInOrder() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CartView cart = twoItemCart();
        CompletableFuture<CartView> first = new CompletableFuture<>();
        CompletableFuture<CartView> second = new CompletableFuture<>();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(cart));
        when(client.updateCartItem(any())).thenReturn(first, second);
        CartPanel panel = onEdt(() -> new CartPanel(client, new ShopNavigator(route -> { }), new RecordingKit(), () -> { }));
        onEdt(panel::load); flushEdt();
        onEdt(() -> { panel.updateQuantity("cart-item-1", 3); panel.updateQuantity("cart-item-2", 4); });
        assertThat(component(panel, "cart.update-cart-item-1", JButton.class).isEnabled()).isFalse();
        assertThat(component(panel, "cart.update-cart-item-2", JButton.class).isEnabled()).isFalse();
        verify(client, org.mockito.Mockito.times(1)).updateCartItem(any());
        first.complete(cart); flushEdt();
        verify(client).updateCartItem(new UpdateCartItemCommand("cart-item-2", 4, 0));
        second.complete(cart); flushEdt();
    }

    @Test
    void cartWriteExpiryRejectsAlreadyInFlightLoadCompletion() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CartView initial = ShopClientFixtures.cartView();
        CartView lateLoadResult = twoItemCart();
        CompletableFuture<CartView> pendingLoad = new CompletableFuture<>();
        CompletableFuture<CartView> pendingWrite = new CompletableFuture<>();
        AtomicInteger sessionExpired = new AtomicInteger();
        RecordingKit kit = new RecordingKit();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(initial), pendingLoad);
        when(client.updateCartItem(any())).thenReturn(pendingWrite);
        CartPanel panel = onEdt(() -> new CartPanel(client, new ShopNavigator(route -> { }),
                kit, sessionExpired::incrementAndGet));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::load);
        verify(client, times(2)).getCart();
        assertThat(pendingLoad).isNotDone();

        onEdt(() -> panel.updateQuantity("cart-item-1", 3));
        verify(client).updateCartItem(new UpdateCartItemCommand("cart-item-1", 3, 0));
        pendingWrite.completeExceptionally(new ShopClientException("AUTH_SESSION_EXPIRED"));
        flushEdt();

        assertThat(sessionExpired).hasValue(1);
        assertStateMounted(panel, kit, "cart.state", ShopPageState.DISCONNECTED);
        StateCall disconnectedState = kit.stateCalls.getLast();
        int disconnectedStateCount = kit.stateCalls.size();

        pendingLoad.complete(lateLoadResult);
        flushEdt();

        assertThat(panel.visibleItems()).containsExactlyElementsOf(initial.items());
        assertThat(kit.stateCalls).hasSize(disconnectedStateCount);
        assertThat(kit.stateCalls.getLast()).isSameAs(disconnectedState);
        assertStateMounted(panel, kit, "cart.state", ShopPageState.DISCONNECTED);
        assertThat(onEdt(() -> containsNamedComponent(panel, "cart.update-cart-item-1"))).isFalse();
        onEdt(() -> {
            panel.load();
            panel.updateQuantity("cart-item-1", 4);
            panel.remove("cart-item-1");
        });

        verify(client, times(2)).getCart();
        verify(client, times(1)).updateCartItem(any());
        verify(client, never()).removeCartItem(any());
        assertThat(sessionExpired).hasValue(1);
        assertThat(kit.stateCalls).hasSize(disconnectedStateCount);
    }

    @Test
    void cartLoadWriteFenceAndSessionExpiry() throws Exception {
        ShopClientPort fencedClient = mock(ShopClientPort.class);
        CartView initial = twoItemCart();
        CartView staleWriteResult = new CartView("stale-write", List.of(), BigDecimal.ZERO);
        CartView authoritative = authoritativeCart();
        CompletableFuture<CartView> firstWrite = new CompletableFuture<>();
        CompletableFuture<CartView> authoritativeLoad = new CompletableFuture<>();
        RecordingKit fencedKit = new RecordingKit();
        when(fencedClient.getCart()).thenReturn(CompletableFuture.completedFuture(initial), authoritativeLoad);
        when(fencedClient.updateCartItem(any())).thenReturn(firstWrite);
        CartPanel fenced = onEdt(() -> new CartPanel(fencedClient,
                new ShopNavigator(route -> { }), fencedKit, () -> { }));

        onEdt(fenced::load);
        flushEdt();
        onEdt(() -> {
            fenced.updateQuantity("cart-item-1", 3);
            fenced.updateQuantity("cart-item-2", 4);
        });
        int statesBeforeRefresh = fencedKit.stateCalls.size();
        onEdt(fenced::load);
        firstWrite.complete(staleWriteResult);
        flushEdt();
        flushEdt();

        verify(fencedClient, times(2)).getCart();
        verify(fencedClient).updateCartItem(new UpdateCartItemCommand("cart-item-1", 3, 0));
        verify(fencedClient, times(1)).updateCartItem(any());
        assertThat(fenced.visibleItems()).containsExactlyElementsOf(initial.items());
        assertThat(fencedKit.statesAfter(statesBeforeRefresh))
                .containsExactly(ShopPageState.LOADING, ShopPageState.LOADING);

        authoritativeLoad.complete(authoritative);
        flushEdt();
        assertThat(fenced.visibleItems()).containsExactlyElementsOf(authoritative.items());
        assertStateMounted(fenced, fencedKit, "cart.state", ShopPageState.NORMAL);

        ShopClientPort staleFailureClient = mock(ShopClientPort.class);
        CompletableFuture<CartView> staleFailure = new CompletableFuture<>();
        RecordingKit staleFailureKit = new RecordingKit();
        when(staleFailureClient.getCart()).thenReturn(
                CompletableFuture.completedFuture(initial),
                CompletableFuture.completedFuture(authoritative));
        when(staleFailureClient.updateCartItem(any())).thenReturn(staleFailure);
        CartPanel staleFailurePanel = onEdt(() -> new CartPanel(staleFailureClient,
                new ShopNavigator(route -> { }), staleFailureKit, () -> { }));
        onEdt(staleFailurePanel::load);
        flushEdt();
        onEdt(() -> staleFailurePanel.updateQuantity("cart-item-1", 3));
        int statesBeforeStaleFailure = staleFailureKit.stateCalls.size();
        onEdt(staleFailurePanel::load);
        staleFailure.completeExceptionally(new ShopClientException("SHOP_UNAVAILABLE"));
        flushEdt();
        flushEdt();
        flushEdt();
        verify(staleFailureClient, times(2)).getCart();
        assertThat(staleFailureKit.statesAfter(statesBeforeStaleFailure))
                .doesNotContain(ShopPageState.ERROR, ShopPageState.SUBMITTING);
        assertThat(staleFailurePanel.visibleItems()).containsExactlyElementsOf(authoritative.items());
        assertStateMounted(staleFailurePanel, staleFailureKit, "cart.state", ShopPageState.NORMAL);

        ShopClientPort writeExpiryClient = mock(ShopClientPort.class);
        CompletableFuture<CartView> expiringWrite = new CompletableFuture<>();
        AtomicInteger writeExpiryEvents = new AtomicInteger();
        RecordingKit writeExpiryKit = new RecordingKit();
        when(writeExpiryClient.getCart()).thenReturn(CompletableFuture.completedFuture(initial));
        when(writeExpiryClient.updateCartItem(any())).thenReturn(expiringWrite);
        CartPanel writeExpiry = onEdt(() -> new CartPanel(writeExpiryClient,
                new ShopNavigator(route -> { }), writeExpiryKit, writeExpiryEvents::incrementAndGet));

        onEdt(writeExpiry::load);
        flushEdt();
        onEdt(() -> {
            writeExpiry.updateQuantity("cart-item-1", 3);
            writeExpiry.updateQuantity("cart-item-2", 4);
        });
        expiringWrite.completeExceptionally(new ShopClientException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        assertStateMounted(writeExpiry, writeExpiryKit, "cart.state", ShopPageState.DISCONNECTED);
        int writeExpiryStateCount = writeExpiryKit.stateCalls.size();
        onEdt(() -> {
            writeExpiry.load();
            writeExpiry.updateQuantity("cart-item-2", 5);
            writeExpiry.remove("cart-item-1");
        });

        assertThat(writeExpiryEvents).hasValue(1);
        assertThat(writeExpiryKit.stateCalls).hasSize(writeExpiryStateCount);
        verify(writeExpiryClient, times(1)).getCart();
        verify(writeExpiryClient, times(1)).updateCartItem(any());
        verify(writeExpiryClient, never()).removeCartItem(any());

        ShopClientPort loadExpiryClient = mock(ShopClientPort.class);
        AtomicInteger loadExpiryEvents = new AtomicInteger();
        RecordingKit loadExpiryKit = new RecordingKit();
        when(loadExpiryClient.getCart()).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("AUTH_SESSION_EXPIRED")));
        CartPanel loadExpiry = onEdt(() -> new CartPanel(loadExpiryClient,
                new ShopNavigator(route -> { }), loadExpiryKit, loadExpiryEvents::incrementAndGet));

        onEdt(loadExpiry::load);
        flushEdt();
        assertStateMounted(loadExpiry, loadExpiryKit, "cart.state", ShopPageState.DISCONNECTED);
        int loadExpiryStateCount = loadExpiryKit.stateCalls.size();
        onEdt(() -> {
            loadExpiry.load();
            loadExpiry.updateQuantity("cart-item-1", 2);
            loadExpiry.remove("cart-item-1");
        });
        flushEdt();

        assertThat(loadExpiryEvents).hasValue(1);
        assertThat(loadExpiryKit.stateCalls).hasSize(loadExpiryStateCount);
        verify(loadExpiryClient, times(1)).getCart();
        verify(loadExpiryClient, never()).updateCartItem(any());
        verify(loadExpiryClient, never()).removeCartItem(any());
    }

    @Test
    void twoItemCheckoutSnapshotAndPriceConfirmationFences() throws Exception {
        CartView cart = twoItemCart();
        CheckoutCommand initialCommand = new CheckoutCommand(List.of(
                new CheckoutItem("cart-item-1", new BigDecimal("3.00")),
                new CheckoutItem("cart-item-2", new BigDecimal("5.00"))), false);
        CheckoutCommand acceptedCommand = new CheckoutCommand(List.of(
                new CheckoutItem("cart-item-1", new BigDecimal("3.00")),
                new CheckoutItem("cart-item-2", new BigDecimal("5.00"))), true);
        ShopClientPort client = mock(ShopClientPort.class);
        RecordingDialogs dialogs = new RecordingDialogs();
        when(client.getCart()).thenReturn(CompletableFuture.completedFuture(cart));
        when(client.checkout(any()))
                .thenReturn(CompletableFuture.failedFuture(new ShopClientException("SHOP_PRICE_CHANGED")))
                .thenReturn(CompletableFuture.failedFuture(new ShopClientException("SHOP_PRICE_CHANGED")))
                .thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()));
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client,
                new ShopNavigator(route -> { }), new RecordingKit(), dialogs, () -> { }, RecordingCashier::new));

        onEdt(panel::load);
        flushEdt();
        onEdt(panel::submit);
        flushEdt();
        verify(client).checkout(initialCommand);
        assertThat(dialogs.confirmedCode()).isEqualTo("SHOP_PRICE_CHANGED");

        onEdt(dialogs::rejectLatest);
        verify(client, times(1)).checkout(any());
        onEdt(panel::submit);
        flushEdt();
        verify(client, times(2)).checkout(initialCommand);
        onEdt(dialogs::acceptLatest);
        flushEdt();
        verify(client).checkout(acceptedCommand);

        ShopClientPort disposedClient = mock(ShopClientPort.class);
        RecordingDialogs disposedDialogs = new RecordingDialogs();
        when(disposedClient.getCart()).thenReturn(CompletableFuture.completedFuture(cart));
        when(disposedClient.checkout(any())).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("SHOP_PRICE_CHANGED")));
        CheckoutPanel disposedPanel = onEdt(() -> new CheckoutPanel(disposedClient,
                new ShopNavigator(route -> { }), new RecordingKit(), disposedDialogs, () -> { },
                RecordingCashier::new));
        onEdt(disposedPanel::load);
        flushEdt();
        onEdt(disposedPanel::submit);
        flushEdt();
        onEdt(disposedPanel::disposePage);
        onEdt(disposedDialogs::acceptLatest);
        verify(disposedClient, times(1)).checkout(initialCommand);

        ShopClientPort reloadedClient = mock(ShopClientPort.class);
        RecordingDialogs reloadedDialogs = new RecordingDialogs();
        CartView replacement = authoritativeCart();
        when(reloadedClient.getCart()).thenReturn(CompletableFuture.completedFuture(cart),
                CompletableFuture.completedFuture(replacement));
        when(reloadedClient.checkout(any())).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("SHOP_PRICE_CHANGED")));
        CheckoutPanel reloadedPanel = onEdt(() -> new CheckoutPanel(reloadedClient,
                new ShopNavigator(route -> { }), new RecordingKit(), reloadedDialogs, () -> { },
                RecordingCashier::new));
        onEdt(reloadedPanel::load);
        flushEdt();
        onEdt(reloadedPanel::submit);
        flushEdt();
        onEdt(reloadedPanel::load);
        flushEdt();
        onEdt(reloadedDialogs::acceptLatest);
        verify(reloadedClient, times(1)).checkout(initialCommand);
    }

    @Test
    void cashierCommandTerminalAndDisposalLifecycles() throws Exception {
        ShopClientPort terminalClient = mock(ShopClientPort.class);
        CompletableFuture<PaymentView> terminalFuture = new CompletableFuture<>();
        List<ShopRoute> terminalRoutes = new ArrayList<>();
        AtomicInteger terminalClosed = new AtomicInteger();
        when(terminalClient.simulatePayment(any())).thenReturn(terminalFuture);
        SimulatedCashierDialog terminalCashier = onEdt(() -> new SimulatedCashierDialog(null,
                terminalClient, new ShopNavigator(terminalRoutes::add), new RecordingKit(),
                ShopClientFixtures.checkoutResult(), () -> { }, terminalClosed::incrementAndGet));

        onEdt(() -> terminalCashier.submit(PaymentChannel.BANK_CARD, PaymentAttemptStatus.FAILED));
        verify(terminalClient).simulatePayment(new SimulatePaymentCommand(
                "payment-1", PaymentChannel.BANK_CARD, PaymentAttemptStatus.FAILED));
        PaymentView expired = payment(PaymentStatus.EXPIRED, null);
        terminalFuture.complete(expired);
        flushEdt();
        assertThat(terminalCashier.isClosed()).isTrue();
        assertThat(terminalClosed).hasValue(1);
        assertThat(terminalRoutes).containsExactly(new ShopRoute.PaymentResult(expired));

        ShopClientPort cartClient = mock(ShopClientPort.class);
        RecordingKit cartKit = new RecordingKit();
        AtomicInteger cartExpiryEvents = new AtomicInteger();
        when(cartClient.getCart()).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("AUTH_SESSION_EXPIRED")));
        CartPanel cart = onEdt(() -> new CartPanel(cartClient, new ShopNavigator(route -> { }),
                cartKit, cartExpiryEvents::incrementAndGet));
        onEdt(cart::load);
        flushEdt();
        assertStateMounted(cart, cartKit, "cart.state", ShopPageState.DISCONNECTED);
        onEdt(cart::load);
        verify(cartClient, times(1)).getCart();
        assertThat(cartExpiryEvents).hasValue(1);

        ShopClientPort checkoutClient = mock(ShopClientPort.class);
        RecordingKit checkoutKit = new RecordingKit();
        AtomicInteger checkoutExpiryEvents = new AtomicInteger();
        CartView checkoutCart = ShopClientFixtures.cartView();
        when(checkoutClient.getCart()).thenReturn(CompletableFuture.completedFuture(checkoutCart),
                CompletableFuture.completedFuture(checkoutCart));
        when(checkoutClient.checkout(any()))
                .thenReturn(CompletableFuture.failedFuture(new ShopClientException("AUTH_SESSION_EXPIRED")))
                .thenReturn(CompletableFuture.completedFuture(ShopClientFixtures.checkoutResult()));
        CheckoutPanel checkout = onEdt(() -> new CheckoutPanel(checkoutClient,
                new ShopNavigator(route -> { }), checkoutKit, new RecordingDialogs(),
                checkoutExpiryEvents::incrementAndGet, RecordingCashier::new));
        onEdt(checkout::load);
        flushEdt();
        onEdt(checkout::submit);
        flushEdt();
        assertStateMounted(checkout, checkoutKit, "checkout.state", ShopPageState.DISCONNECTED);
        int checkoutStateCount = checkoutKit.stateCalls.size();
        onEdt(() -> {
            checkout.load();
            checkout.submit();
        });
        flushEdt();
        verify(checkoutClient, times(1)).getCart();
        verify(checkoutClient, times(1)).checkout(any());
        assertThat(checkoutExpiryEvents).hasValue(1);
        assertThat(checkoutKit.stateCalls).hasSize(checkoutStateCount);

        ShopClientPort checkoutLoadExpiryClient = mock(ShopClientPort.class);
        RecordingKit checkoutLoadExpiryKit = new RecordingKit();
        AtomicInteger checkoutLoadExpiryEvents = new AtomicInteger();
        when(checkoutLoadExpiryClient.getCart()).thenReturn(CompletableFuture.failedFuture(
                new ShopClientException("AUTH_SESSION_EXPIRED")));
        CheckoutPanel checkoutLoadExpiry = onEdt(() -> new CheckoutPanel(checkoutLoadExpiryClient,
                new ShopNavigator(route -> { }), checkoutLoadExpiryKit, new RecordingDialogs(),
                checkoutLoadExpiryEvents::incrementAndGet, RecordingCashier::new));
        onEdt(checkoutLoadExpiry::load);
        flushEdt();
        assertStateMounted(checkoutLoadExpiry, checkoutLoadExpiryKit,
                "checkout.state", ShopPageState.DISCONNECTED);
        int checkoutLoadExpiryStateCount = checkoutLoadExpiryKit.stateCalls.size();
        onEdt(checkoutLoadExpiry::load);
        flushEdt();
        verify(checkoutLoadExpiryClient, times(1)).getCart();
        assertThat(checkoutLoadExpiryEvents).hasValue(1);
        assertThat(checkoutLoadExpiryKit.stateCalls).hasSize(checkoutLoadExpiryStateCount);

        ShopClientPort cashierExpiryClient = mock(ShopClientPort.class);
        RecordingKit cashierExpiryKit = new RecordingKit();
        AtomicInteger cashierExpiryEvents = new AtomicInteger();
        when(cashierExpiryClient.simulatePayment(any()))
                .thenReturn(CompletableFuture.failedFuture(new ShopClientException("AUTH_SESSION_EXPIRED")))
                .thenReturn(CompletableFuture.completedFuture(payment(PaymentStatus.PENDING, null)));
        SimulatedCashierDialog cashierExpiry = onEdt(() -> new SimulatedCashierDialog(null,
                cashierExpiryClient, new ShopNavigator(route -> { }), cashierExpiryKit,
                ShopClientFixtures.checkoutResult(), cashierExpiryEvents::incrementAndGet));
        onEdt(() -> cashierExpiry.submit(PaymentChannel.ALIPAY, PaymentAttemptStatus.FAILED));
        flushEdt();
        assertStateMounted(cashierExpiry, cashierExpiryKit, "cashier.state", ShopPageState.DISCONNECTED);
        assertThat(cashierExpiry.retryEnabled()).isFalse();
        int cashierExpiryStateCount = cashierExpiryKit.stateCalls.size();
        onEdt(() -> cashierExpiry.submit(PaymentChannel.ALIPAY, PaymentAttemptStatus.SUCCEEDED));
        flushEdt();
        verify(cashierExpiryClient, times(1)).simulatePayment(any());
        assertThat(cashierExpiryEvents).hasValue(1);
        assertThat(cashierExpiryKit.stateCalls).hasSize(cashierExpiryStateCount);
        onEdt(cashierExpiry::disposePage);

        ShopClientPort checkoutDisposeClient = mock(ShopClientPort.class);
        CompletableFuture<CheckoutResult> pendingCheckout = new CompletableFuture<>();
        RecordingCashierFactory cashierFactory = new RecordingCashierFactory();
        when(checkoutDisposeClient.getCart()).thenReturn(CompletableFuture.completedFuture(checkoutCart));
        when(checkoutDisposeClient.checkout(any())).thenReturn(pendingCheckout);
        CheckoutPanel checkoutDispose = onEdt(() -> new CheckoutPanel(checkoutDisposeClient,
                new ShopNavigator(route -> { }), new RecordingKit(), new RecordingDialogs(),
                () -> { }, cashierFactory));
        onEdt(checkoutDispose::load);
        flushEdt();
        onEdt(checkoutDispose::submit);
        onEdt(checkoutDispose::disposePage);
        Thread checkoutWorker = new Thread(() -> pendingCheckout.complete(ShopClientFixtures.checkoutResult()));
        checkoutWorker.start();
        checkoutWorker.join();
        flushEdt();
        assertThat(checkoutDispose.currentCheckout()).isNull();
        assertThat(cashierFactory.created).isEmpty();

        ShopClientPort paymentDisposeClient = mock(ShopClientPort.class);
        CompletableFuture<PaymentView> pendingPayment = new CompletableFuture<>();
        List<ShopRoute> lateRoutes = new ArrayList<>();
        AtomicInteger lateClosed = new AtomicInteger();
        RecordingKit paymentDisposeKit = new RecordingKit();
        when(paymentDisposeClient.simulatePayment(any())).thenReturn(pendingPayment);
        SimulatedCashierDialog paymentDispose = onEdt(() -> new SimulatedCashierDialog(null,
                paymentDisposeClient, new ShopNavigator(lateRoutes::add), paymentDisposeKit,
                ShopClientFixtures.checkoutResult(), () -> { }, lateClosed::incrementAndGet));
        onEdt(() -> paymentDispose.submit(PaymentChannel.WECHAT, PaymentAttemptStatus.SUCCEEDED));
        onEdt(paymentDispose::disposePage);
        int stateCallsAtClose = paymentDisposeKit.stateCalls.size();
        Thread paymentWorker = new Thread(() -> pendingPayment.complete(
                payment(PaymentStatus.SUCCEEDED, PaymentChannel.WECHAT)));
        paymentWorker.start();
        paymentWorker.join();
        flushEdt();
        assertThat(lateRoutes).isEmpty();
        assertThat(lateClosed).hasValue(1);
        assertThat(paymentDisposeKit.stateCalls).hasSize(stateCallsAtClose);
    }

    @Test
    void cashierDisablesOnlyInitiatingAttemptButton() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PaymentView> failedAttempt = new CompletableFuture<>();
        CompletableFuture<PaymentView> cancelledAttempt = new CompletableFuture<>();
        CompletableFuture<PaymentView> succeededAttempt = new CompletableFuture<>();
        when(client.simulatePayment(any())).thenReturn(
                failedAttempt, cancelledAttempt, succeededAttempt);
        SimulatedCashierDialog cashier = onEdt(() -> new SimulatedCashierDialog(null, client,
                new ShopNavigator(route -> { }), new RecordingKit(),
                ShopClientFixtures.checkoutResult(), () -> { }));
        List<PaymentAttemptStatus> attempts = List.of(
                PaymentAttemptStatus.FAILED,
                PaymentAttemptStatus.CANCELLED,
                PaymentAttemptStatus.SUCCEEDED);
        List<String> buttonNames = List.of(
                "cashier.failed",
                "cashier.cancel",
                "cashier.success");
        List<CompletableFuture<PaymentView>> futures = List.of(
                failedAttempt, cancelledAttempt, succeededAttempt);

        for (int attemptIndex = 0; attemptIndex < attempts.size(); attemptIndex++) {
            int currentIndex = attemptIndex;
            onEdt(() -> component(cashier, buttonNames.get(currentIndex), JButton.class).doClick());

            for (int buttonIndex = 0; buttonIndex < buttonNames.size(); buttonIndex++) {
                boolean expectedEnabled = buttonIndex != attemptIndex;
                assertThat(component(cashier, buttonNames.get(buttonIndex), JButton.class).isEnabled())
                        .as("%s while %s is in flight", buttonNames.get(buttonIndex), attempts.get(attemptIndex))
                        .isEqualTo(expectedEnabled);
            }
            verify(client).simulatePayment(new SimulatePaymentCommand(
                    "payment-1", PaymentChannel.WECHAT, attempts.get(attemptIndex)));
            verify(client, times(attemptIndex + 1)).simulatePayment(any());

            int otherButton = (attemptIndex + 1) % buttonNames.size();
            onEdt(() -> component(cashier, buttonNames.get(otherButton), JButton.class).doClick());
            verify(client, times(attemptIndex + 1)).simulatePayment(any());

            futures.get(attemptIndex).complete(payment(PaymentStatus.PENDING, null));
            flushEdt();
            for (String buttonName : buttonNames) {
                assertThat(component(cashier, buttonName, JButton.class).isEnabled()).isTrue();
            }
        }
        onEdt(cashier::disposePage);
    }

    @Test
    void paymentResultShowsExactReceiptAndRoutes() throws Exception {
        PaymentView receipt = payment(PaymentStatus.SUCCEEDED, PaymentChannel.BANK_CARD);
        List<ShopRoute> routes = new ArrayList<>();
        PaymentResultPanel panel = onEdt(() -> new PaymentResultPanel(
                new ShopNavigator(routes::add), new RecordingKit(), receipt));

        assertThat(component(panel, "payment-number", JLabel.class).getText()).isEqualTo("P0001");
        assertThat(component(panel, "payment-amount", JLabel.class).getText()).isEqualTo("¥6.00");
        assertThat(component(panel, "payment-channel", JLabel.class).getText()).isEqualTo("BANK_CARD");
        assertThat(component(panel, "payment-status", JLabel.class).getText()).isEqualTo("SUCCEEDED");

        onEdt(() -> component(panel, "payment-result.home", JButton.class).doClick());
        onEdt(() -> component(panel, "payment-result.orders", JButton.class).doClick());
        assertThat(routes).containsExactly(
                new ShopRoute.Home(new HomeProductQuery(null, null,
                        ProductSortMode.SALES_DESC, 0, 20)),
                new ShopRoute.My());
    }

    @Test
    void buyerStateViewsAreMountedOnEdt() throws Exception {
        verifyCartStateViews();
        verifyCheckoutStateViews();
        verifyCashierStateViews();

        RecordingKit resultKit = new RecordingKit();
        PaymentResultPanel result = onEdt(() -> new PaymentResultPanel(
                new ShopNavigator(route -> { }), resultKit,
                payment(PaymentStatus.SUCCEEDED, PaymentChannel.ALIPAY)));
        assertStateMounted(result, resultKit, "payment-result.state", ShopPageState.NORMAL);
        assertThat(resultKit.states).containsExactlyInAnyOrder(ShopPageState.NORMAL);
    }

    private static void verifyCartStateViews() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<CartView> normalLoad = new CompletableFuture<>();
        CompletableFuture<CartView> emptyLoad = new CompletableFuture<>();
        CompletableFuture<CartView> failedLoad = new CompletableFuture<>();
        CompletableFuture<CartView> expiredLoad = new CompletableFuture<>();
        CompletableFuture<CartView> failedWrite = new CompletableFuture<>();
        AtomicInteger sessionExpired = new AtomicInteger();
        RecordingKit kit = new RecordingKit();
        when(client.getCart()).thenReturn(normalLoad, emptyLoad, failedLoad, expiredLoad);
        when(client.updateCartItem(any())).thenReturn(failedWrite);
        CartPanel panel = onEdt(() -> new CartPanel(client, new ShopNavigator(route -> { }),
                kit, sessionExpired::incrementAndGet));

        assertStateMounted(panel, kit, "cart.state", ShopPageState.INITIAL);
        onEdt(panel::load);
        assertStateMounted(panel, kit, "cart.state", ShopPageState.LOADING);
        normalLoad.complete(ShopClientFixtures.cartView());
        flushEdt();
        assertStateMounted(panel, kit, "cart.state", ShopPageState.NORMAL);
        onEdt(() -> panel.updateQuantity("cart-item-1", 3));
        assertStateMounted(panel, kit, "cart.state", ShopPageState.SUBMITTING);
        failedWrite.completeExceptionally(new ShopClientException("SHOP_UNAVAILABLE"));
        flushEdt();
        assertStateMounted(panel, kit, "cart.state", ShopPageState.ERROR);

        onEdt(panel::load);
        assertStateMounted(panel, kit, "cart.state", ShopPageState.LOADING);
        emptyLoad.complete(new CartView("empty", List.of(), BigDecimal.ZERO));
        flushEdt();
        assertStateMounted(panel, kit, "cart.state", ShopPageState.EMPTY);

        onEdt(panel::load);
        assertStateMounted(panel, kit, "cart.state", ShopPageState.LOADING);
        failedLoad.completeExceptionally(new ShopClientException("SHOP_UNAVAILABLE"));
        flushEdt();
        assertStateMounted(panel, kit, "cart.state", ShopPageState.ERROR);

        onEdt(panel::load);
        assertStateMounted(panel, kit, "cart.state", ShopPageState.LOADING);
        expiredLoad.completeExceptionally(new ShopClientException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        assertStateMounted(panel, kit, "cart.state", ShopPageState.DISCONNECTED);
        assertThat(sessionExpired).hasValue(1);
        assertThat(kit.states).containsExactlyInAnyOrder(
                ShopPageState.INITIAL,
                ShopPageState.LOADING,
                ShopPageState.NORMAL,
                ShopPageState.EMPTY,
                ShopPageState.ERROR,
                ShopPageState.DISCONNECTED,
                ShopPageState.SUBMITTING);
        onEdt(panel::disposePage);
    }

    private static void verifyCheckoutStateViews() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<CartView> normalLoad = new CompletableFuture<>();
        CompletableFuture<CartView> emptyLoad = new CompletableFuture<>();
        CompletableFuture<CartView> failedLoad = new CompletableFuture<>();
        CompletableFuture<CartView> expiredLoad = new CompletableFuture<>();
        CompletableFuture<CheckoutResult> failedCheckout = new CompletableFuture<>();
        AtomicInteger sessionExpired = new AtomicInteger();
        RecordingKit kit = new RecordingKit();
        when(client.getCart()).thenReturn(normalLoad, emptyLoad, failedLoad, expiredLoad);
        when(client.checkout(any())).thenReturn(failedCheckout);
        CheckoutPanel panel = onEdt(() -> new CheckoutPanel(client,
                new ShopNavigator(route -> { }), kit, new RecordingDialogs(),
                sessionExpired::incrementAndGet, RecordingCashier::new));

        assertStateMounted(panel, kit, "checkout.state", ShopPageState.INITIAL);
        onEdt(panel::load);
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.LOADING);
        normalLoad.complete(ShopClientFixtures.cartView());
        flushEdt();
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.NORMAL);
        onEdt(panel::submit);
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.SUBMITTING);
        failedCheckout.completeExceptionally(new ShopClientException("SHOP_INSUFFICIENT_STOCK"));
        flushEdt();
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.ERROR);

        onEdt(panel::load);
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.LOADING);
        emptyLoad.complete(new CartView("empty", List.of(), BigDecimal.ZERO));
        flushEdt();
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.EMPTY);

        onEdt(panel::load);
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.LOADING);
        failedLoad.completeExceptionally(new ShopClientException("SHOP_UNAVAILABLE"));
        flushEdt();
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.ERROR);

        onEdt(panel::load);
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.LOADING);
        expiredLoad.completeExceptionally(new ShopClientException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        assertStateMounted(panel, kit, "checkout.state", ShopPageState.DISCONNECTED);
        assertThat(sessionExpired).hasValue(1);
        assertThat(kit.states).containsExactlyInAnyOrder(
                ShopPageState.INITIAL,
                ShopPageState.LOADING,
                ShopPageState.NORMAL,
                ShopPageState.EMPTY,
                ShopPageState.ERROR,
                ShopPageState.DISCONNECTED,
                ShopPageState.SUBMITTING);
        onEdt(panel::disposePage);
    }

    private static void verifyCashierStateViews() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PaymentView> pending = new CompletableFuture<>();
        CompletableFuture<PaymentView> failed = new CompletableFuture<>();
        CompletableFuture<PaymentView> expired = new CompletableFuture<>();
        AtomicInteger sessionExpired = new AtomicInteger();
        RecordingKit kit = new RecordingKit();
        when(client.simulatePayment(any())).thenReturn(pending, failed, expired);
        SimulatedCashierDialog cashier = onEdt(() -> new SimulatedCashierDialog(null, client,
                new ShopNavigator(route -> { }), kit, ShopClientFixtures.checkoutResult(),
                sessionExpired::incrementAndGet));

        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.INITIAL);
        onEdt(() -> cashier.submit(PaymentChannel.WECHAT, PaymentAttemptStatus.FAILED));
        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.SUBMITTING);
        pending.complete(payment(PaymentStatus.PENDING, null));
        flushEdt();
        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.NORMAL);

        onEdt(() -> cashier.submit(PaymentChannel.WECHAT, PaymentAttemptStatus.FAILED));
        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.SUBMITTING);
        failed.completeExceptionally(new ShopClientException("SHOP_UNAVAILABLE"));
        flushEdt();
        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.ERROR);

        onEdt(() -> cashier.submit(PaymentChannel.WECHAT, PaymentAttemptStatus.FAILED));
        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.SUBMITTING);
        expired.completeExceptionally(new ShopClientException("AUTH_SESSION_EXPIRED"));
        flushEdt();
        assertStateMounted(cashier, kit, "cashier.state", ShopPageState.DISCONNECTED);
        assertThat(sessionExpired).hasValue(1);
        assertThat(kit.states).containsExactlyInAnyOrder(
                ShopPageState.INITIAL,
                ShopPageState.NORMAL,
                ShopPageState.ERROR,
                ShopPageState.DISCONNECTED,
                ShopPageState.SUBMITTING);
        onEdt(cashier::disposePage);
    }

    private static void assertStateMounted(Container root, RecordingKit kit,
            String expectedName, ShopPageState expectedState) throws Exception {
        StateCall call = kit.stateCalls.getLast();
        assertThat(call.name()).isEqualTo(expectedName);
        assertThat(call.state()).isEqualTo(expectedState);
        assertThat(call.edt()).isTrue();
        assertThat(onEdt(() -> component(root, expectedName, JComponent.class)))
                .isSameAs(call.component());
        assertThat(onEdt(() -> javax.swing.SwingUtilities.isDescendingFrom(call.component(), root)))
                .isTrue();
    }

    private static boolean containsNamedComponent(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return true;
            if (child instanceof Container nested && containsNamedComponent(nested, name)) return true;
        }
        return false;
    }

    private static PaymentView payment(PaymentStatus status, PaymentChannel channel) {
        return new PaymentView("payment-1", "group-1", "P0001", new BigDecimal("6.00"), status,
                channel, Instant.parse("2026-08-29T00:15:00Z"), null, 0);
    }

    private static CartView twoItemCart() {
        CartView first = ShopClientFixtures.cartView();
        return new CartView(first.cartId(), List.of(first.items().getFirst(), new CartItemView(
                "cart-item-2", "product-2", "笔记本", "sku-2", "A5", "shop-1", "校园文具店",
                new BigDecimal("5.00"), 1, 0)), new BigDecimal("11.00"));
    }

    private static CartView authoritativeCart() {
        CartItemView item = twoItemCart().items().get(1);
        return new CartView("cart-authoritative", List.of(item), new BigDecimal("5.00"));
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
        void rejectLatest() { accepted = null; }
    }

    private static final class RecordingKit implements ShopUiKit {
        private final EnumSet<ShopPageState> states = EnumSet.noneOf(ShopPageState.class);
        private final List<Boolean> uiThreadCalls = new ArrayList<>();
        private final List<StateCall> stateCalls = new ArrayList<>();

        @Override public JButton navigationButton(String name, String text) { return named(new JButton(text), name); }
        @Override public JButton primaryButton(String name, String text) { return named(new JButton(text), name); }
        @Override public JButton secondaryButton(String name, String text) { return named(new JButton(text), name); }
        @Override public JPanel filterPanel(String name, LayoutManager layout) { return named(new JPanel(layout), name); }
        @Override public JPanel productCard(String name, LayoutManager layout) { return named(new JPanel(layout), name); }
        @Override public JComponent stateView(String name, ShopPageState state, String message, Runnable retry) {
            boolean edt = javax.swing.SwingUtilities.isEventDispatchThread();
            JComponent component = named(new JLabel(message), name);
            states.add(state);
            uiThreadCalls.add(edt);
            stateCalls.add(new StateCall(name, state, component, edt));
            return component;
        }
        List<ShopPageState> statesAfter(int index) {
            return stateCalls.subList(index, stateCalls.size()).stream().map(StateCall::state).toList();
        }
        private static <T extends JComponent> T named(T component, String name) {
            component.setName(name); return component;
        }
    }

    private record StateCall(String name, ShopPageState state, JComponent component, boolean edt) { }

    private static final class RecordingCashierFactory implements CheckoutPanel.CashierFactory {
        private final List<RecordingCashier> created = new ArrayList<>();

        @Override
        public CheckoutPanel.ActiveCashier create(java.awt.Window owner, ShopClientPort client,
                ShopNavigator navigator, ShopUiKit uiKit, CheckoutResult checkout,
                Runnable sessionExpired, java.util.function.Consumer<PaymentView> terminal,
                Runnable closed) {
            RecordingCashier cashier = new RecordingCashier(owner, client, navigator, uiKit, checkout,
                    sessionExpired, terminal, closed);
            created.add(cashier);
            return cashier;
        }
    }

    private static final class RecordingCashier implements CheckoutPanel.ActiveCashier {
        private final Runnable closed;
        private final java.util.function.Consumer<PaymentView> terminal;
        private boolean closedState;

        private RecordingCashier(java.awt.Window owner, ShopClientPort client, ShopNavigator navigator,
                ShopUiKit uiKit, CheckoutResult checkout, Runnable sessionExpired,
                java.util.function.Consumer<PaymentView> terminal, Runnable closed) {
            this.terminal = terminal;
            this.closed = closed;
        }
        @Override public void open() { }
        @Override public void disposePage() { close(); }
        @Override public boolean isClosed() { return closedState; }
        void close() { if (!closedState) { closedState = true; closed.run(); } }
        void complete(PaymentView payment) { terminal.accept(payment); }
    }
}
