package edu.seu.vcampus.server.shop.catalog;

import edu.seu.vcampus.common.protocol.*;
import edu.seu.vcampus.common.shop.catalog.CatalogDtos.*;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.port.ShopUserKind;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import java.io.Serializable;

/** Session-authenticated Socket routes, deriving identities and idempotency keys on the server. */
public final class CatalogHandlers {
    private final CatalogService catalog;
    private final CartService cart;
    private final ImportService imports;
    private final ShopUserPort users;
    /** Registers catalog, seller management, cart and structured import routes. */
    public CatalogHandlers(MessageRouter router, CatalogService catalog, CartService cart,
            ImportService imports, ShopUserPort users) {
        this.catalog = catalog; this.cart = cart; this.imports = imports; this.users = users;
        for (String route : new String[]{"CATALOG_LIST", "CATALOG_DETAIL", "CATALOG_SHOPS", "CATALOG_SHOP",
                "PRODUCT_LIST", "PRODUCT_DETAIL", "PRODUCT_SAVE", "PRODUCT_ACTION", "PRODUCT_IMAGES",
                "PRODUCT_ADMIN_LIST", "PRODUCT_ADMIN_DETAIL", "CART_GET", "CART_CHANGE", "CART_BULK",
                "IMPORT_PREVIEW", "IMPORT_CONFIRM"}) {
            router.register("SHOP2_" + route, (message, context) -> handle(message));
        }
    }
    private ResponseBody<? extends Serializable> handle(Message message) {
        try {
            var actor = users.requireUser(message.sessionToken());
            if (!actor.active()) throw new SecurityException();
            if (message.type() != MessageType.REQUEST) throw new CatalogException("请求类型无效");
            String user = actor.userId();
            if (message.command().startsWith("SHOP2_PRODUCT_ADMIN_") && actor.kind() != ShopUserKind.ADMINISTRATOR)
                throw new SecurityException();
            Serializable result = switch (message.command()) {
                case "SHOP2_CATALOG_LIST" -> catalog.list(body(message, Query.class));
                case "SHOP2_CATALOG_DETAIL" -> catalog.detail(body(message, String.class));
                case "SHOP2_CATALOG_SHOPS" -> catalog.shops(body(message, Query.class));
                case "SHOP2_CATALOG_SHOP" -> catalog.shop(body(message, String.class));
                case "SHOP2_PRODUCT_LIST" -> catalog.ownedList(user, body(message, Query.class));
                case "SHOP2_PRODUCT_DETAIL" -> catalog.ownedDetail(user, body(message, String.class));
                case "SHOP2_PRODUCT_ADMIN_LIST" -> catalog.adminList(body(message, Query.class));
                case "SHOP2_PRODUCT_ADMIN_DETAIL" -> catalog.adminDetail(body(message, String.class));
                case "SHOP2_PRODUCT_SAVE" -> {
                    var p = body(message, SaveProduct.class);
                    yield catalog.save(user, new SaveProduct(message.requestId(), p.id(), p.name(), p.description(),
                            p.category(), p.imageId(), p.defaultSkuId(), p.skus()));
                }
                case "SHOP2_PRODUCT_ACTION" -> {
                    var a = body(message, ProductAction.class);
                    yield catalog.action(user, new ProductAction(message.requestId(), a.productId(), a.action()));
                }
                case "SHOP2_PRODUCT_IMAGES" -> {
                    var a = body(message, Images.class);
                    yield catalog.images(user, new Images(message.requestId(), a.productIds(), a.imageId()));
                }
                case "SHOP2_CART_GET" -> { body(message, EmptyRequest.class); yield cart.get(user); }
                case "SHOP2_CART_CHANGE" -> {
                    var a = body(message, CartChange.class);
                    yield cart.change(user, new CartChange(message.requestId(), a.itemId(), a.skuId(), a.quantity()));
                }
                case "SHOP2_CART_BULK" -> {
                    var a = body(message, BulkAdd.class);
                    yield cart.bulk(user, new BulkAdd(message.requestId(), a.productIds()));
                }
                case "SHOP2_IMPORT_PREVIEW" -> imports.preview(user, body(message, ImportCommand.class));
                case "SHOP2_IMPORT_CONFIRM" -> {
                    var a = body(message, ImportCommand.class);
                    yield imports.confirm(user, new ImportCommand(message.requestId(), a.rows()));
                }
                default -> throw new CatalogException("未知请求");
            };
            return ResponseBody.success(result);
        } catch (CatalogException error) { return ResponseBody.failure("SHOP_CATALOG_INVALID_REQUEST", error.getMessage(), null); }
        catch (ShopAccessException error) { return failure(error.code()); }
        catch (SecurityException error) { return failure("AUTH_FORBIDDEN"); }
        catch (RuntimeException error) { return failure("SHOP_CATALOG_RETRY_REQUIRED"); }
    }
    private static <T> T body(Message message, Class<T> type) {
        if (!type.isInstance(message.body())) throw new CatalogException("请求内容无效");
        return type.cast(message.body());
    }
    private static ResponseBody<EmptyResponse> failure(String code) {
        return ResponseBody.failure(code, "商城请求未完成，请检查登录状态或稍后重试", null);
    }
}
