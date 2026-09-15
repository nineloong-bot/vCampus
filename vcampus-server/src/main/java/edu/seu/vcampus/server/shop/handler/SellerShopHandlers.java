package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.shop.SaveSellerDraftCommand;
import edu.seu.vcampus.common.shop.SubmitSellerApplicationCommand;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.service.SellerApplicationService;
import edu.seu.vcampus.server.shop.service.SellerService;
import edu.seu.vcampus.server.shop.service.ProductService;
import edu.seu.vcampus.server.shop.service.SellerOrderService;
import edu.seu.vcampus.common.shop.*;
import edu.seu.vcampus.common.protocol.EmptyResponse;

import java.util.Objects;

/** Registers seller application commands. */
public final class SellerShopHandlers {
    /**
     * Creates a seller shop handlers with its required collaborators.
     * @param router the router
     * @param users the users
     * @param deduplicator the deduplicator
     * @param applications the applications
     * @param log the log
     */
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

    /**
     * Creates a seller shop handlers with its required collaborators.
     * @param router the router
     * @param users the users
     * @param deduplicator the deduplicator
     * @param applications the applications
     * @param sellers the sellers
     * @param products the products
     * @param orders the orders
     * @param log the log
     */
    public SellerShopHandlers(MessageRouter router, ShopUserPort users,
            RequestDeduplicator deduplicator, SellerApplicationService applications,
            SellerService sellers, ProductService products, SellerOrderService orders,
            ShopBusinessLogger log) {
        this(router, users, deduplicator, applications, log);
        ShopHandlerSupport support = new ShopHandlerSupport(users, deduplicator, log);
        router.register("SHOP_SELLER_GET_SHOP", support.read(EmptyRequest.class,
                (token, ignored) -> sellers.getOwnedShop(token)));
        router.register("SHOP_SELLER_UPDATE_SHOP", support.write(UpdateShopCommand.class,
                products::updateShop));
        router.register("SHOP_SELLER_SEARCH_PRODUCTS", support.read(ProductManagementQuery.class,
                products::searchOwnedProducts));
        router.register("SHOP_SELLER_GET_PRODUCT", support.read(String.class,
                products::getOwnedProduct));
        router.register("SHOP_SELLER_CREATE_PRODUCT", support.write(CreateProductCommand.class,
                products::createProduct));
        router.register("SHOP_SELLER_UPDATE_PRODUCT", support.write(UpdateProductCommand.class,
                products::updateProduct));
        router.register("SHOP_SELLER_CHANGE_PRODUCT_STATUS", support.write(
                ChangeProductStatusCommand.class, (token, command) -> {
                    products.changeProductStatus(token, command);
                    return EmptyResponse.INSTANCE;
                }));
        router.register("SHOP_SELLER_GET_ORDERS", support.read(SellerOrderQuery.class,
                orders::getOwnedOrders));
    }
}
