package edu.seu.vcampus.client.shop.service;

import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.shop.*;

import java.util.concurrent.CompletableFuture;

public interface AdminShopClientPort {
    CompletableFuture<PageResult<SellerApplicationView>> searchApplications(SellerApplicationQuery query);
    CompletableFuture<SellerApplicationView> reviewApplication(ReviewSellerApplicationCommand command);
    CompletableFuture<PageResult<ShopAdminSummary>> searchShops(ShopAdminQuery query);
    CompletableFuture<EmptyResponse> suspendShop(SuspendShopCommand command);
    CompletableFuture<EmptyResponse> resumeShop(ResumeShopCommand command);
}
