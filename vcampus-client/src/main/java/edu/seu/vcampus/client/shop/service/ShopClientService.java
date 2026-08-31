package edu.seu.vcampus.client.shop.service;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.*;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Type-safe asynchronous gateway for the authenticated buyer Shop surface. */
public final class ShopClientService implements ShopClientPort, SellerShopClientPort, AdminShopClientPort {
    private final ClientConnection connection;
    private final Duration timeout;

    public ShopClientService(ClientConnection connection, Duration timeout) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query) {
        return send("SHOP_HOME", query);
    }

    @Override
    public CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query) {
        return send("SHOP_SEARCH_PRODUCTS", query);
    }

    @Override
    public CompletableFuture<ProductDetail> getProduct(String productId) {
        return send("SHOP_GET_PRODUCT", productId);
    }

    @Override
    public CompletableFuture<ShopDetail> getShop(String shopId) {
        return send("SHOP_GET_SHOP", shopId);
    }

    @Override
    public CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query) {
        return send("SHOP_GET_SHOP_PRODUCTS", query);
    }

    @Override
    public CompletableFuture<CartView> getCart() {
        return send("SHOP_GET_CART", EmptyRequest.INSTANCE);
    }

    @Override
    public CompletableFuture<PaidOrderHistory> getPaidOrders() {
        return send("SHOP_GET_PAID_ORDERS", EmptyRequest.INSTANCE);
    }

    @Override
    public CompletableFuture<CartView> addToCart(AddCartItemCommand command) {
        return send("SHOP_CART_ADD", command);
    }

    @Override
    public CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command) {
        return send("SHOP_CART_UPDATE", command);
    }

    @Override
    public CompletableFuture<CartView> removeCartItem(String cartItemId) {
        return send("SHOP_CART_REMOVE", cartItemId);
    }

    @Override
    public CompletableFuture<CheckoutResult> checkout(CheckoutCommand command) {
        return send("SHOP_CHECKOUT", command);
    }

    @Override
    public CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command) {
        return send("SHOP_SIMULATE_PAYMENT", command);
    }

    @Override
    public CompletableFuture<Optional<SellerApplicationView>> getMyApplication() {
        return sendNullable("SHOP_SELLER_GET_APPLICATION", EmptyRequest.INSTANCE);
    }

    @Override
    public CompletableFuture<SellerApplicationView> saveApplication(SaveSellerDraftCommand command) {
        return send("SHOP_SELLER_SAVE_APPLICATION", command);
    }

    @Override
    public CompletableFuture<SellerApplicationView> submitApplication(SubmitSellerApplicationCommand command) {
        return send("SHOP_SELLER_SUBMIT_APPLICATION", command);
    }

    @Override
    public CompletableFuture<PageResult<SellerApplicationView>> searchApplications(SellerApplicationQuery query) {
        return send("SHOP_ADMIN_SEARCH_APPLICATIONS", query);
    }

    @Override
    public CompletableFuture<SellerApplicationView> reviewApplication(ReviewSellerApplicationCommand command) {
        return send("SHOP_ADMIN_REVIEW_APPLICATION", command);
    }

    @Override
    public CompletableFuture<PageResult<ShopAdminSummary>> searchShops(ShopAdminQuery query) {
        return send("SHOP_ADMIN_SEARCH_SHOPS", query);
    }

    @Override
    public CompletableFuture<EmptyResponse> suspendShop(SuspendShopCommand command) {
        return send("SHOP_ADMIN_SUSPEND_SHOP", command);
    }

    @Override
    public CompletableFuture<EmptyResponse> resumeShop(ResumeShopCommand command) {
        return send("SHOP_ADMIN_RESUME_SHOP", command);
    }

    private <T extends Serializable> CompletableFuture<T> send(String command, Serializable body) {
        return CompletableFuture
                .supplyAsync(() -> connection.<T>send(command, body, timeout))
                .thenCompose(response -> response)
                .thenApply(this::requireData);
    }

    private <T extends Serializable> CompletableFuture<Optional<T>> sendNullable(
            String command, Serializable body) {
        return CompletableFuture
                .supplyAsync(() -> connection.<T>send(command, body, timeout))
                .thenCompose(response -> response)
                .thenApply(response -> {
                    if (response == null) {
                        throw new ShopClientException("COMMON_INTERNAL_ERROR");
                    }
                    if (!response.success()) {
                        throw new ShopClientException(response.code() == null
                                ? "COMMON_INTERNAL_ERROR" : response.code());
                    }
                    return Optional.ofNullable(response.data());
                });
    }

    private <T extends Serializable> T requireData(ResponseBody<T> response) {
        if (response == null) {
            throw new ShopClientException("COMMON_INTERNAL_ERROR");
        }
        if (!response.success() || response.data() == null) {
            throw new ShopClientException(response.code() == null
                    ? "COMMON_INTERNAL_ERROR" : response.code());
        }
        return response.data();
    }
}
