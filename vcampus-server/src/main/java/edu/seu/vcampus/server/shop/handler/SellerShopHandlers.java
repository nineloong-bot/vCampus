package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.service.SellerApplicationService;

import java.util.Objects;

/** Registers seller application commands. */
public final class SellerShopHandlers {
    public SellerShopHandlers(MessageRouter router, ShopUserPort users,
            RequestDeduplicator deduplicator, SellerApplicationService applications,
            ShopBusinessLogger log) {
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(applications, "applications");
        ShopHandlerSupport support = new ShopHandlerSupport(users, deduplicator, log);
        router.register("SHOP_SELLER_GET_APPLICATION", support.read(EmptyRequest.class,
                (token, ignored) -> applications.findMyApplication(token).orElse(null)));
        router.register("SHOP_SELLER_SAVE_APPLICATION", support.write(SaveSellerDraftCommand.class,
                applications::saveDraft));
        router.register("SHOP_SELLER_SUBMIT_APPLICATION", support.write(SubmitSellerApplicationCommand.class,
                applications::submitApplication));
    }
}
