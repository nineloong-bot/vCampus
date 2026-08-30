package edu.seu.vcampus.client.shop.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.*;

import java.util.concurrent.CompletableFuture;

public interface ShopClientPort {
    CompletableFuture<PageResult<ProductSummary>> home(HomeProductQuery query);
    CompletableFuture<PageResult<ProductSummary>> search(ProductSearchQuery query);
    CompletableFuture<ProductDetail> getProduct(String productId);
    CompletableFuture<ShopDetail> getShop(String shopId);
    CompletableFuture<PageResult<ProductSummary>> getShopProducts(ShopProductQuery query);
    CompletableFuture<CartView> getCart();
    CompletableFuture<PaidOrderHistory> getPaidOrders();
    CompletableFuture<CartView> addToCart(AddCartItemCommand command);
    CompletableFuture<CartView> updateCartItem(UpdateCartItemCommand command);
    CompletableFuture<CartView> removeCartItem(String cartItemId);
    CompletableFuture<CheckoutResult> checkout(CheckoutCommand command);
    CompletableFuture<PaymentView> simulatePayment(SimulatePaymentCommand command);
}
