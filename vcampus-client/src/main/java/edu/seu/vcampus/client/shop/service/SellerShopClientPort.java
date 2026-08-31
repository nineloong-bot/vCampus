package edu.seu.vcampus.client.shop.service;

import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.shop.*;

public interface SellerShopClientPort {
    CompletableFuture<Optional<SellerApplicationView>> getMyApplication();
    CompletableFuture<SellerApplicationView> saveApplication(SaveSellerDraftCommand command);
    CompletableFuture<SellerApplicationView> submitApplication(SubmitSellerApplicationCommand command);
    CompletableFuture<ShopView> getOwnedShop();
    CompletableFuture<ShopView> updateOwnedShop(UpdateShopCommand command);
    CompletableFuture<PageResult<ProductManagementSummary>> searchOwnedProducts(
            ProductManagementQuery query);
    CompletableFuture<ProductView> createOwnedProduct(CreateProductCommand command);
    CompletableFuture<ProductView> updateOwnedProduct(UpdateProductCommand command);
    CompletableFuture<EmptyResponse> changeOwnedProductStatus(ChangeProductStatusCommand command);
    CompletableFuture<SellerOrderHistory> getOwnedOrders(SellerOrderQuery query);
}
