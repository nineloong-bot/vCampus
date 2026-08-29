package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductSearchQuery;

import java.util.Objects;

/** The complete set of buyer pages hosted by the Shop UI. */
public sealed interface ShopRoute permits ShopRoute.Home, ShopRoute.Search,
        ShopRoute.Product, ShopRoute.Storefront, ShopRoute.Cart,
        ShopRoute.Checkout, ShopRoute.PaymentResult {
    record Home(HomeProductQuery query) implements ShopRoute {
        public Home { Objects.requireNonNull(query, "query"); }
    }

    record Search(ProductSearchQuery query) implements ShopRoute {
        public Search { Objects.requireNonNull(query, "query"); }
    }

    record Product(String productId) implements ShopRoute {
        public Product { Objects.requireNonNull(productId, "productId"); }
    }

    record Storefront(String shopId) implements ShopRoute {
        public Storefront { Objects.requireNonNull(shopId, "shopId"); }
    }

    record Cart() implements ShopRoute { }

    record Checkout() implements ShopRoute { }

    record PaymentResult(PaymentView payment) implements ShopRoute {
        public PaymentResult { Objects.requireNonNull(payment, "payment"); }
    }
}
