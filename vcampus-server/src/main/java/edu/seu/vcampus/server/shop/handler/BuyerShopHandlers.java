package edu.seu.vcampus.server.shop.handler;

import edu.seu.vcampus.common.protocol.Message;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.shop.AddCartItemCommand;
import edu.seu.vcampus.common.shop.CartView;
import edu.seu.vcampus.common.shop.CheckoutCommand;
import edu.seu.vcampus.common.shop.CheckoutResult;
import edu.seu.vcampus.common.protocol.EmptyRequest;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductDetail;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ShopDetail;
import edu.seu.vcampus.common.shop.ShopProductQuery;
import edu.seu.vcampus.common.shop.SimulatePaymentCommand;
import edu.seu.vcampus.common.shop.UpdateCartItemCommand;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.server.routing.ClientContext;
import edu.seu.vcampus.server.routing.MessageRouter;
import edu.seu.vcampus.server.routing.RequestDeduplicator;
import edu.seu.vcampus.server.shop.ShopException;
import edu.seu.vcampus.server.shop.logging.ShopBusinessLogger;
import edu.seu.vcampus.server.shop.payment.SimulatedPaymentService;
import edu.seu.vcampus.server.shop.port.ShopAccessException;
import edu.seu.vcampus.server.shop.port.ShopUser;
import edu.seu.vcampus.server.shop.port.ShopUserPort;
import edu.seu.vcampus.server.shop.service.CartService;
import edu.seu.vcampus.server.shop.service.CheckoutService;
import edu.seu.vcampus.server.shop.service.ShopService;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.BiFunction;

/** Registers the authenticated buyer-facing Shop protocol surface. */
public final class BuyerShopHandlers {
    private final ShopUserPort users;
    private final RequestDeduplicator deduplicator;
    private final ShopService shop;
    private final CartService cart;
    private final CheckoutService checkout;
    private final SimulatedPaymentService payment;
    private final ShopBusinessLogger businessLog;

    public BuyerShopHandlers(MessageRouter router, ShopUserPort users,
            RequestDeduplicator deduplicator, ShopService shop, CartService cart,
            CheckoutService checkout, SimulatedPaymentService payment,
            ShopBusinessLogger businessLog) {
        Objects.requireNonNull(router, "router");
        this.users = Objects.requireNonNull(users, "users");
        this.deduplicator = Objects.requireNonNull(deduplicator, "deduplicator");
        this.shop = Objects.requireNonNull(shop, "shop");
        this.cart = Objects.requireNonNull(cart, "cart");
        this.checkout = Objects.requireNonNull(checkout, "checkout");
        this.payment = Objects.requireNonNull(payment, "payment");
        this.businessLog = Objects.requireNonNull(businessLog, "businessLog");

        router.register("SHOP_HOME", read(HomeProductQuery.class, (token, body) -> shop.getHomeProducts(body)));
        router.register("SHOP_SEARCH_PRODUCTS", read(ProductSearchQuery.class, (token, body) -> shop.searchProducts(body)));
        router.register("SHOP_GET_PRODUCT", read(String.class, (token, body) -> shop.getProduct(body)));
        router.register("SHOP_GET_SHOP", read(String.class, (token, body) -> shop.getShop(body)));
        router.register("SHOP_GET_SHOP_PRODUCTS", read(ShopProductQuery.class, (token, body) -> shop.getShopProducts(body)));
        router.register("SHOP_GET_CART", read(EmptyRequest.class, (token, body) -> cart.getCart(token)));
        router.register("SHOP_CART_ADD", write(AddCartItemCommand.class, (token, body) -> cart.addToCart(token, body)));
        router.register("SHOP_CART_UPDATE", write(UpdateCartItemCommand.class, (token, body) -> cart.updateCartItem(token, body)));
        router.register("SHOP_CART_REMOVE", write(String.class, (token, body) -> cart.removeCartItem(token, body)));
        router.register("SHOP_CHECKOUT", write(CheckoutCommand.class, (token, body) -> checkout.checkout(token, body)));
        router.register("SHOP_SIMULATE_PAYMENT", write(SimulatePaymentCommand.class, (token, body) -> payment.simulatePayment(token, body)));
    }

    private <T extends Serializable, R extends Serializable> edu.seu.vcampus.server.routing.MessageHandler read(
            Class<T> type, BiFunction<String, T, R> operation) {
        return (message, context) -> {
            try {
                ShopUser actor = users.requireUser(message.sessionToken());
                return finish(message, actor.userId(), () -> execute(message, type, operation,
                        message.sessionToken(), actor.userId()));
            } catch (RuntimeException error) {
                return failure(message, error);
            }
        };
    }

    private <T extends Serializable, R extends Serializable> edu.seu.vcampus.server.routing.MessageHandler write(
            Class<T> type, BiFunction<String, T, R> operation) {
        return (message, context) -> {
            try {
                ShopUser actor = users.requireUser(message.sessionToken());
                return finish(message, actor.userId(), () -> deduplicator.executeOnce(message, actor.userId(), context.connectionId(),
                    () -> {
                        ResponseBody<R> response = execute(message, type, operation,
                                message.sessionToken(), actor.userId());
                        if (response.success() && response.data() instanceof CheckoutResult result) {
                            businessLog.checkoutSucceeded(message, actor.userId(),
                                    (CheckoutCommand) message.body(), result);
                        } else if (response.success() && response.data() instanceof PaymentView result) {
                            businessLog.paymentCompleted(message, actor.userId(), result);
                        }
                        return response;
                    }));
            } catch (RuntimeException error) {
                return failure(message, error);
            }
        };
    }

    private <T extends Serializable, R extends Serializable> ResponseBody<R> execute(
            Message message, Class<T> type, BiFunction<String, T, R> operation,
            String token, String userId) {
        try {
            T body = type.cast(message.body());
            return ResponseBody.success(operation.apply(token, body));
        } catch (ShopException error) {
            return ResponseBody.failure(error.code().name(), "Shop request failed", null);
        } catch (ShopAccessException error) {
            return ResponseBody.failure(error.code(), "Authentication failed", null);
        } catch (IllegalArgumentException | NullPointerException | ClassCastException | SecurityException error) {
            return ResponseBody.failure("COMMON_VALIDATION_FAILED", "Invalid request", null);
        } catch (RuntimeException error) {
            return ResponseBody.failure("COMMON_INTERNAL_ERROR", "Internal error", null);
        }
    }

    private ResponseBody<? extends Serializable> finish(Message message, String userId,
            java.util.function.Supplier<ResponseBody<? extends Serializable>> action) {
        long started = System.nanoTime();
        ResponseBody<? extends Serializable> response;
        try {
            response = action.get();
        } catch (ShopAccessException error) {
            response = ResponseBody.failure(error.code(), "Authentication failed", null);
        } catch (IllegalArgumentException | NullPointerException | ClassCastException | SecurityException error) {
            response = ResponseBody.failure("COMMON_VALIDATION_FAILED", "Invalid request", null);
        } catch (RuntimeException error) {
            response = ResponseBody.failure("COMMON_INTERNAL_ERROR", "Internal error", null);
        }
        businessLog.commandCompleted(message, userId, response.code(), elapsed(started));
        return response;
    }

    private ResponseBody<? extends Serializable> failure(Message message, RuntimeException error) {
        ResponseBody<? extends Serializable> response;
        if (error instanceof ShopAccessException access) {
            response = ResponseBody.failure(access.code(), "Authentication failed", null);
        } else if (error instanceof ShopException shopError) {
            response = ResponseBody.failure(shopError.code().name(), "Shop request failed", null);
        } else if (error instanceof IllegalArgumentException || error instanceof NullPointerException
                || error instanceof ClassCastException || error instanceof SecurityException) {
            response = ResponseBody.failure("COMMON_VALIDATION_FAILED", "Invalid request", null);
        } else {
            response = ResponseBody.failure("COMMON_INTERNAL_ERROR", "Internal error", null);
        }
        businessLog.commandCompleted(message, "anonymous", response.code(), 0);
        return response;
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
