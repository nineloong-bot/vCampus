package edu.seu.vcampus.client.shop.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.shop.ShopClientFixtures;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ShopClientServiceTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void sendsCartAddAndReturnsTypedCart() {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        CartView expected = ShopClientFixtures.cartView();
        when(connection.<CartView>send(eq("SHOP_CART_ADD"),
                eq(new AddCartItemCommand("sku-1", 2)), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)));

        assertThat(service.addToCart(new AddCartItemCommand("sku-1", 2)).join())
                .isEqualTo(expected);
    }

    @Test
    void dispatchesSocketSendOffEdtAndPreservesTypedResponse() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        CartView expected = ShopClientFixtures.cartView();
        AtomicBoolean sendRanOnEdt = new AtomicBoolean();
        when(connection.<CartView>send(eq("SHOP_CART_ADD"),
                eq(new AddCartItemCommand("sku-1", 2)), eq(TIMEOUT)))
                .thenAnswer(invocation -> {
                    sendRanOnEdt.set(SwingUtilities.isEventDispatchThread());
                    return CompletableFuture.completedFuture(ResponseBody.success(expected));
                });

        CompletableFuture<CartView> response = edu.seu.vcampus.client.shop.ShopSwingTestSupport
                .onEdt((Callable<CompletableFuture<CartView>>) () ->
                        service.addToCart(new AddCartItemCommand("sku-1", 2)));

        assertThat(response.join()).isEqualTo(expected);
        assertThat(sendRanOnEdt).isFalse();
    }

    @Test
    void preservesStableServerCodeAndUsesEmptyCartRequest() {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        when(connection.<CartView>send(eq("SHOP_GET_CART"), eq(EmptyRequest.INSTANCE), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(
                        ResponseBody.failure("AUTH_SESSION_EXPIRED", "expired", null)));

        assertThatThrownBy(() -> service.getCart().join())
                .hasRootCauseInstanceOf(ShopClientException.class)
                .hasRootCauseMessage("AUTH_SESSION_EXPIRED");
    }

    @Test
    void sendsPaidOrderHistoryCommandWithEmptyRequest() {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        PaidOrderHistory expected = new PaidOrderHistory(List.of());
        when(connection.<PaidOrderHistory>send(eq("SHOP_GET_PAID_ORDERS"),
                eq(EmptyRequest.INSTANCE), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(expected)));

        assertThat(service.getPaidOrders().join()).isEqualTo(expected);
        verify(connection).send("SHOP_GET_PAID_ORDERS", EmptyRequest.INSTANCE, TIMEOUT);
    }

    @Test
    void paidOrderHistoryPreservesStableServerErrorCode() {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        when(connection.<PaidOrderHistory>send(eq("SHOP_GET_PAID_ORDERS"),
                eq(EmptyRequest.INSTANCE), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(
                        ResponseBody.failure("AUTH_SESSION_EXPIRED", "expired", null)));

        assertThatThrownBy(() -> service.getPaidOrders().join())
                .hasRootCauseInstanceOf(ShopClientException.class)
                .hasRootCauseMessage("AUTH_SESSION_EXPIRED");
    }

    @Test
    void rejectsSuccessfulResponseWithNullDataAndPreservesStableCode() {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        when(connection.<CartView>send(eq("SHOP_GET_CART"), eq(EmptyRequest.INSTANCE), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(
                        new ResponseBody<>(true, "SHOP_CART_EMPTY", "成功", null, null)));

        assertThatThrownBy(() -> service.getCart().join())
                .hasRootCauseInstanceOf(ShopClientException.class)
                .hasRootCauseMessage("SHOP_CART_EMPTY");
    }

    @Test
    void sendsEveryBuyerCommandWithItsTypedBody() {
        ClientConnection connection = mock(ClientConnection.class);
        ShopClientService service = new ShopClientService(connection, TIMEOUT);
        HomeProductQuery home = new HomeProductQuery(null, null, ProductSortMode.SALES_DESC, 0, 20);
        ProductSearchQuery search = new ProductSearchQuery("笔", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);
        ShopProductQuery shopProducts = new ShopProductQuery("shop-1", null, null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);
        UpdateCartItemCommand update = new UpdateCartItemCommand("cart-item-1", 3, 0);
        CheckoutCommand checkout = new CheckoutCommand(java.util.List.of(), false);
        SimulatePaymentCommand payment = new SimulatePaymentCommand("payment-1",
                PaymentChannel.ALIPAY, PaymentAttemptStatus.SUCCEEDED);
        when(connection.<PageResult<ProductSummary>>send(eq("SHOP_HOME"), eq(home), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.page(ShopClientFixtures.productSummary()))));
        when(connection.<PageResult<ProductSummary>>send(eq("SHOP_SEARCH_PRODUCTS"), eq(search), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.page(ShopClientFixtures.productSummary()))));
        when(connection.<ProductDetail>send(eq("SHOP_GET_PRODUCT"), eq("product-1"), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.productDetail())));
        when(connection.<ShopDetail>send(eq("SHOP_GET_SHOP"), eq("shop-1"), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.shopDetail())));
        when(connection.<PageResult<ProductSummary>>send(eq("SHOP_GET_SHOP_PRODUCTS"), eq(shopProducts), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.page(ShopClientFixtures.productSummary()))));
        when(connection.<CartView>send(eq("SHOP_CART_UPDATE"), eq(update), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.cartView())));
        when(connection.<CartView>send(eq("SHOP_CART_REMOVE"), eq("cart-item-1"), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.cartView())));
        when(connection.<CheckoutResult>send(eq("SHOP_CHECKOUT"), eq(checkout), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.checkoutResult())));
        when(connection.<PaymentView>send(eq("SHOP_SIMULATE_PAYMENT"), eq(payment), eq(TIMEOUT)))
                .thenReturn(CompletableFuture.completedFuture(ResponseBody.success(ShopClientFixtures.paymentView())));

        assertThat(service.home(home).join().items()).hasSize(1);
        assertThat(service.search(search).join().items()).hasSize(1);
        assertThat(service.getProduct("product-1").join()).isEqualTo(ShopClientFixtures.productDetail());
        assertThat(service.getShop("shop-1").join()).isEqualTo(ShopClientFixtures.shopDetail());
        assertThat(service.getShopProducts(shopProducts).join().items()).hasSize(1);
        assertThat(service.updateCartItem(update).join()).isEqualTo(ShopClientFixtures.cartView());
        assertThat(service.removeCartItem("cart-item-1").join()).isEqualTo(ShopClientFixtures.cartView());
        assertThat(service.checkout(checkout).join()).isEqualTo(ShopClientFixtures.checkoutResult());
        assertThat(service.simulatePayment(payment).join()).isEqualTo(ShopClientFixtures.paymentView());
        verify(connection).send("SHOP_HOME", home, TIMEOUT);
        verify(connection).send("SHOP_SEARCH_PRODUCTS", search, TIMEOUT);
        verify(connection).send("SHOP_GET_PRODUCT", "product-1", TIMEOUT);
        verify(connection).send("SHOP_GET_SHOP", "shop-1", TIMEOUT);
        verify(connection).send("SHOP_GET_SHOP_PRODUCTS", shopProducts, TIMEOUT);
        verify(connection).send("SHOP_CART_UPDATE", update, TIMEOUT);
        verify(connection).send("SHOP_CART_REMOVE", "cart-item-1", TIMEOUT);
        verify(connection).send("SHOP_CHECKOUT", checkout, TIMEOUT);
        verify(connection).send("SHOP_SIMULATE_PAYMENT", payment, TIMEOUT);
    }
}
