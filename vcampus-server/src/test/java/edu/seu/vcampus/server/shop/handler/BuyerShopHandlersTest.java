package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.MessageType;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.payment.SimulatedPaymentService;
import edu.seu.vcampus.server.shop.service.CartService;
import edu.seu.vcampus.server.shop.service.BuyerOrderService;
import edu.seu.vcampus.server.shop.service.CheckoutService;
import edu.seu.vcampus.server.shop.service.ShopService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BuyerShopHandlersTest {
    private final MessageRouter router = new MessageRouter(Map.of());
    private final ShopUserPort users = mock(ShopUserPort.class);
    private final RequestDeduplicator deduplicator = mock(RequestDeduplicator.class);
    private final ShopService shop = mock(ShopService.class);
    private final CartService cart = mock(CartService.class);
    private final CheckoutService checkout = mock(CheckoutService.class);
    private final BuyerOrderService orders = mock(BuyerOrderService.class);
    private final SimulatedPaymentService payment = mock(SimulatedPaymentService.class);
    private final ShopBusinessLogger businessLog = mock(ShopBusinessLogger.class);

    @Test
    void registersBuyerSurfaceAndReplaysIdenticalCartWrite() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        Message add = request("request-add", "SHOP_CART_ADD",
                new AddCartItemCommand("sku-1", 2));
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        when(deduplicator.executeOnce(eq(add), eq("buyer-1"), eq("connection-1"), any()))
                .thenAnswer(invocation -> invocation.<Supplier<ResponseBody<CartView>>>getArgument(3).get());
        when(cart.addToCart("buyer-token", (AddCartItemCommand) add.body()))
                .thenReturn(cartView());

        assertThat(router.route(add, context()).code()).isEqualTo("SUCCESS");
        verify(cart).addToCart("buyer-token", new AddCartItemCommand("sku-1", 2));
        verify(deduplicator).executeOnce(eq(add), eq("buyer-1"),
                eq("connection-1"), any());
        verify(businessLog).commandCompleted(eq(add), eq("buyer-1"), eq("SUCCESS"), anyLong());
    }

    @Test
    void registersExactlyTwelveBuyerCommands() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        when(shop.getHomeProducts(any())).thenReturn(page());
        when(shop.searchProducts(any())).thenReturn(page());
        when(shop.getProduct(any())).thenReturn(null);
        when(shop.getShop(any())).thenReturn(null);
        when(shop.getShopProducts(any())).thenReturn(page());
        when(cart.getCart(any())).thenReturn(cartView());
        when(orders.getPaidOrders(any())).thenReturn(new PaidOrderHistory(List.of()));
        when(deduplicator.executeOnce(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<ResponseBody<?>>>getArgument(3).get());
        for (Message message : List.of(
                request("h", "SHOP_HOME", new HomeProductQuery(null, null, null, 0, 20)),
                request("s", "SHOP_SEARCH_PRODUCTS", new ProductSearchQuery(null, null, null, null, null, 0, 20)),
                request("p", "SHOP_GET_PRODUCT", "product-1"),
                request("st", "SHOP_GET_SHOP", "shop-1"),
                request("sp", "SHOP_GET_SHOP_PRODUCTS", new ShopProductQuery("shop-1", null, null, null, null, null, 0, 20)),
                request("g", "SHOP_GET_CART", EmptyRequest.INSTANCE),
                request("o", "SHOP_GET_PAID_ORDERS", EmptyRequest.INSTANCE),
                request("a", "SHOP_CART_ADD", new AddCartItemCommand("sku-1", 1)),
                request("u", "SHOP_CART_UPDATE", new UpdateCartItemCommand("item-1", 1, 0)),
                request("r", "SHOP_CART_REMOVE", "item-1"),
                request("c", "SHOP_CHECKOUT", new CheckoutCommand(List.of(), false)),
                request("pay", "SHOP_SIMULATE_PAYMENT", mock(SimulatePaymentCommand.class)))) {
            assertThat(router.route(message, context())).isNotNull();
        }
    }

    @Test
    void paidOrdersUseOnlySessionActorIdAndRequireEmptyRequestBody() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        PaidOrderHistory expected = new PaidOrderHistory(List.of());
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-from-session", ShopUserKind.STUDENT, true));
        when(orders.getPaidOrders("buyer-from-session")).thenReturn(expected);

        ResponseBody<?> response = router.route(
                request("paid-orders", "SHOP_GET_PAID_ORDERS", EmptyRequest.INSTANCE), context());

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.data()).isEqualTo(expected);
        verify(users).requireUser("buyer-token");
        verify(orders).getPaidOrders("buyer-from-session");

        ResponseBody<?> forged = router.route(
                request("forged-paid-orders", "SHOP_GET_PAID_ORDERS", "other-1"), context());
        assertThat(forged.code()).isEqualTo("COMMON_VALIDATION_FAILED");
        verify(orders, times(1)).getPaidOrders(any());
    }

    @Test
    void mapsShopAndAuthenticationFailuresToStableCodes() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        when(users.requireUser("expired"))
                .thenThrow(new ShopAccessException("AUTH_SESSION_EXPIRED"));
        Message expired = new Message("request-expired", MessageType.REQUEST,
                "SHOP_GET_CART", "expired", EmptyRequest.INSTANCE, 1L);
        assertThat(router.route(expired, context()).code())
                .isEqualTo("AUTH_SESSION_EXPIRED");

        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        when(shop.getProduct("missing"))
                .thenThrow(new ShopException(edu.seu.vcampus.common.shop.ShopErrorCode.SHOP_NOT_FOUND,
                        "internal detail"));
        Message missing = request("request-missing", "SHOP_GET_PRODUCT", "missing");
        ResponseBody<?> response = router.route(missing, context());
        assertThat(response.code()).isEqualTo("SHOP_NOT_FOUND");
        assertThat(response.message()).isNotEqualTo("internal detail");
    }

    @Test
    void rejectsNullBodyAsValidationFailure() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        Message request = new Message("null-cart", MessageType.REQUEST,
                "SHOP_GET_CART", "buyer-token", null, 1L);
        assertThat(router.route(request, context()).code())
                .isEqualTo("COMMON_VALIDATION_FAILED");
        verify(cart, never()).getCart(any());
    }

    @Test
    void logsBusinessEventInsideFirstCheckoutExecutionAndCompletionAfterReplay() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        CheckoutCommand command = new CheckoutCommand(List.of(), false);
        CheckoutResult result = mock(CheckoutResult.class);
        when(checkout.checkout("buyer-token", command)).thenReturn(result);
        when(deduplicator.executeOnce(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<ResponseBody<CheckoutResult>>>getArgument(3).get());
        Message request = request("checkout-1", "SHOP_CHECKOUT", command);
        router.route(request, context());
        InOrder order = inOrder(deduplicator, checkout, businessLog);
        order.verify(deduplicator).executeOnce(eq(request), eq("buyer-1"), eq("connection-1"), any());
        order.verify(checkout).checkout("buyer-token", command);
        order.verify(businessLog).checkoutSucceeded(request, "buyer-1", command, result);
        order.verify(businessLog).commandCompleted(eq(request), eq("buyer-1"), eq("SUCCESS"), anyLong());
    }

    @Test
    void replaysCheckoutByRequestIdWithoutRepeatingBusinessEvent() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        CheckoutCommand command = new CheckoutCommand(List.of(), false);
        CheckoutResult result = mock(CheckoutResult.class);
        when(checkout.checkout("buyer-token", command)).thenReturn(result);
        Map<String, ResponseBody<?>> cache = new ConcurrentHashMap<>();
        when(deduplicator.executeOnce(any(), any(), any(), any())).thenAnswer(invocation -> {
            Message request = invocation.getArgument(0);
            @SuppressWarnings("unchecked") Supplier<ResponseBody<?>> action = invocation.getArgument(3);
            return cache.computeIfAbsent(request.requestId(), ignored -> action.get());
        });
        Message request = request("checkout-replay", "SHOP_CHECKOUT", command);
        assertThat(router.route(request, context()).code()).isEqualTo("SUCCESS");
        assertThat(router.route(request, context()).code()).isEqualTo("SUCCESS");
        verify(checkout, times(1)).checkout("buyer-token", command);
        verify(businessLog, times(1)).checkoutSucceeded(request, "buyer-1", command, result);
        verify(businessLog, times(2)).commandCompleted(eq(request), eq("buyer-1"), eq("SUCCESS"), anyLong());
    }

    @Test
    void replaysPaymentByRequestIdWithoutRepeatingBusinessEvent() {
        new BuyerShopHandlers(router, users, deduplicator, shop, cart,
                checkout, orders, payment, businessLog);
        when(users.requireUser("buyer-token"))
                .thenReturn(new ShopUser("buyer-1", ShopUserKind.STUDENT, true));
        SimulatePaymentCommand command = new SimulatePaymentCommand("payment-1",
                edu.seu.vcampus.common.shop.PaymentChannel.ALIPAY,
                edu.seu.vcampus.common.shop.PaymentAttemptStatus.SUCCEEDED);
        PaymentView result = mock(PaymentView.class);
        when(payment.simulatePayment("buyer-token", command)).thenReturn(result);
        Map<String, ResponseBody<?>> cache = new ConcurrentHashMap<>();
        when(deduplicator.executeOnce(any(), any(), any(), any())).thenAnswer(invocation -> {
            Message request = invocation.getArgument(0);
            @SuppressWarnings("unchecked") Supplier<ResponseBody<?>> action = invocation.getArgument(3);
            return cache.computeIfAbsent(request.requestId(), ignored -> action.get());
        });
        Message request = request("payment-replay", "SHOP_SIMULATE_PAYMENT", command);
        assertThat(router.route(request, context()).code()).isEqualTo("SUCCESS");
        assertThat(router.route(request, context()).code()).isEqualTo("SUCCESS");
        verify(payment, times(1)).simulatePayment("buyer-token", command);
        verify(businessLog, times(1)).paymentCompleted(request, "buyer-1", result);
        verify(businessLog, times(2)).commandCompleted(eq(request), eq("buyer-1"), eq("SUCCESS"), anyLong());
    }

    private static Message request(String requestId, String command, java.io.Serializable body) {
        return new Message(requestId, MessageType.REQUEST, command, "buyer-token", body, 1L);
    }

    private static ClientContext context() {
        return new ClientContext("connection-1", "127.0.0.1");
    }

    private static CartView cartView() {
        return new CartView("cart-1", List.of(), BigDecimal.ZERO);
    }

    private static PageResult<ProductSummary> page() {
        return new PageResult<>(List.of(), 0, 20, 0);
    }
}
