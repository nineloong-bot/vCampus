package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ShopProductQuery;

import java.util.Objects;

/** The complete set of buyer pages hosted by the Shop UI. */
public sealed interface ShopRoute permits ShopRoute.Home, ShopRoute.Search,
        ShopRoute.Product, ShopRoute.Storefront, ShopRoute.Cart,
        ShopRoute.Checkout, ShopRoute.PaymentResult, ShopRoute.My,
        ShopRoute.SellerApplication, ShopRoute.SellerWorkspace, ShopRoute.AdminWorkspace {
    static Home defaultHome() {
        return new Home(new HomeProductQuery(null, null,
                ProductSortMode.SALES_DESC, 0, 20));
    }

    record Home(HomeViewState state) implements ShopRoute {
        public Home { Objects.requireNonNull(state, "state"); }
        public Home(HomeProductQuery query) { this(new HomeViewState(query, 0)); }
        public HomeProductQuery query() { return state.query(); }
    }

    record Search(SearchViewState state) implements ShopRoute {
        public Search { Objects.requireNonNull(state, "state"); }
        public Search(ProductSearchQuery query) { this(new SearchViewState(query, false, false, 0)); }
        public ProductSearchQuery query() { return state.query(); }
    }

    record Product(String productId) implements ShopRoute {
        public Product { Objects.requireNonNull(productId, "productId"); }
    }

    record Storefront(StorefrontViewState state) implements ShopRoute {
        public Storefront { Objects.requireNonNull(state, "state"); }
        public Storefront(String shopId) {
            this(new StorefrontViewState(new ShopProductQuery(shopId, null, null, null, null,
                    ProductSortMode.SALES_DESC, 0, 20), 0));
        }
        public Storefront(ShopProductQuery query) { this(new StorefrontViewState(query, 0)); }
        public String shopId() { return state.query().shopId(); }
    }

    record Cart() implements ShopRoute { }

    record Checkout() implements ShopRoute { }

    record PaymentResult(PaymentView payment) implements ShopRoute {
        public PaymentResult { Objects.requireNonNull(payment, "payment"); }
    }

    record My() implements ShopRoute { }

    record SellerApplication() implements ShopRoute { }

    record SellerWorkspace() implements ShopRoute { }

    record AdminWorkspace() implements ShopRoute { }
}
