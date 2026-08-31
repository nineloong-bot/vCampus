package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.shop.ResumeShopCommand;
import edu.seu.vcampus.common.shop.ReviewSellerApplicationCommand;
import edu.seu.vcampus.common.shop.SellerApplicationQuery;
import edu.seu.vcampus.common.shop.ShopAdminQuery;
import edu.seu.vcampus.common.shop.SuspendShopCommand;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.service.ShopAdminService;

import java.util.Objects;

/** Registers administrative application and shop-status commands. */
public final class AdminShopHandlers {
    public AdminShopHandlers(MessageRouter router, ShopUserPort users,
            RequestDeduplicator deduplicator, ShopAdminService admin,
            ShopBusinessLogger log) {
        Objects.requireNonNull(router, "router");
        Objects.requireNonNull(admin, "admin");
        ShopHandlerSupport support = new ShopHandlerSupport(users, deduplicator, log);
        router.register("SHOP_ADMIN_SEARCH_APPLICATIONS", support.read(SellerApplicationQuery.class,
                admin::searchApplications));
        router.register("SHOP_ADMIN_REVIEW_APPLICATION", support.write(ReviewSellerApplicationCommand.class,
                admin::reviewApplication));
        router.register("SHOP_ADMIN_SEARCH_SHOPS", support.read(ShopAdminQuery.class,
                admin::searchShops));
        router.register("SHOP_ADMIN_SUSPEND_SHOP", support.write(SuspendShopCommand.class,
                (token, command) -> {
                    admin.suspendShop(token, command);
                    return EmptyResponse.INSTANCE;
                }));
        router.register("SHOP_ADMIN_RESUME_SHOP", support.write(ResumeShopCommand.class,
                (token, command) -> {
                    admin.resumeShop(token, command);
                    return EmptyResponse.INSTANCE;
                }));
    }
}
