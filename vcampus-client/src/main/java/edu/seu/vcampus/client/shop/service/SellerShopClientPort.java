package edu.seu.vcampus.client.shop.service;

import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SellerShopClientPort {
    CompletableFuture<Optional<SellerApplicationView>> getMyApplication();
    CompletableFuture<SellerApplicationView> saveApplication(SaveSellerDraftCommand command);
    CompletableFuture<SellerApplicationView> submitApplication(SubmitSellerApplicationCommand command);
}
