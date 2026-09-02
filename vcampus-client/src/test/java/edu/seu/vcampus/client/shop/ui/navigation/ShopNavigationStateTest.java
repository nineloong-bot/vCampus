package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.PaymentChannel;
import edu.seu.vcampus.common.shop.PaymentStatus;
import edu.seu.vcampus.common.shop.PaymentView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShopNavigationStateTest {
    @Test
    void productAndDiscoveryRoutesReplaceTheirSemanticLayer() {
        ShopRoute.Home home = ShopRoute.defaultHome();
        ShopRoute.Search search = new ShopRoute.Search(search("笔"));
        ShopNavigationState state = ShopNavigationState.empty()
                .open(home)
                .open(search)
                .open(new ShopRoute.Product("p-1"))
                .open(new ShopRoute.Product("p-2"));

        assertThat(state.current()).contains(new ShopRoute.Product("p-2"));
        assertThat(state.backTargets()).containsExactly(home, search);
        state = state.back();
        assertThat(state.current()).contains(search);
        state = state.open(new ShopRoute.Storefront("shop-1"));
        assertThat(state.backTargets()).containsExactly(home);
    }

    @Test
    void repeatedStoreAndProductBrowsingNeverGrowsTheState() {
        ShopNavigationState state = ShopNavigationState.empty().open(ShopRoute.defaultHome());
        for (int index : IntStream.range(0, 100).toArray()) {
            state = state.open(new ShopRoute.Storefront("shop-" + index));
            state = state.open(new ShopRoute.Product("product-" + index));
        }

        assertThat(state.nodeCount()).isEqualTo(3);
        assertThat(state.backTargets()).hasSize(2);
        assertThat(state.back().back().current()).contains(ShopRoute.defaultHome());
    }

    @Test
    void defaultHomeAlwaysUsesFirstPageAndTopScroll() {
        ShopRoute.Home home = ShopRoute.defaultHome();

        assertThat(home.query().pageNumber()).isZero();
        assertThat(home.query().pageSize()).isEqualTo(20);
        assertThat(home.state().scrollY()).isZero();
    }

    @Test
    void cartAndCheckoutProductPreviewsReturnToTheirOwner() {
        ShopNavigationState cart = ShopNavigationState.empty()
                .open(ShopRoute.defaultHome())
                .open(new ShopRoute.Cart())
                .open(new ShopRoute.Product("cart-product"));
        assertThat(cart.back().current()).contains(new ShopRoute.Cart());

        ShopNavigationState checkout = cart.back()
                .open(new ShopRoute.Checkout())
                .open(new ShopRoute.Product("checkout-product"));
        assertThat(checkout.back().current()).contains(new ShopRoute.Checkout());
        assertThat(checkout.back().back().current()).contains(new ShopRoute.Cart());
    }

    @Test
    void utilitySwitchingReplacesOneRootAndPreservesContentAnchor() {
        ShopNavigationState state = ShopNavigationState.empty()
                .open(ShopRoute.defaultHome())
                .open(new ShopRoute.Storefront("shop-1"))
                .open(new ShopRoute.Product("p-1"))
                .open(new ShopRoute.My())
                .open(new ShopRoute.Cart())
                .open(new ShopRoute.My());

        assertThat(state.back().current()).contains(new ShopRoute.Product("p-1"));
        assertThat(state.nodeCount()).isLessThanOrEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 10, 100})
    void userReportedCycleIsBoundedAndAlwaysReturnsHome(int repetitions) {
        ShopNavigationState state = ShopNavigationState.empty().open(ShopRoute.defaultHome());
        for (int index = 0; index < repetitions; index++) {
            state = state.open(new ShopRoute.Product("home-" + index));
            state = state.open(new ShopRoute.Cart());
            state = state.open(new ShopRoute.Product("cart-" + index));
            state = state.open(new ShopRoute.Storefront("shop-" + index));
            state = state.open(new ShopRoute.Product("store-" + index));
            state = state.open(new ShopRoute.Cart());
        }

        assertThat(state.nodeCount()).isLessThanOrEqualTo(4);
        for (int index = 0; index < 4 && state.canGoBack(); index++) state = state.back();
        assertThat(state.current()).contains(ShopRoute.defaultHome());
        assertThat(state.canGoBack()).isFalse();
    }

    @Test
    void completedPaymentClearsCheckoutFromEveryBackTarget() {
        ShopRoute.PaymentResult receipt = new ShopRoute.PaymentResult(payment());
        ShopNavigationState state = ShopNavigationState.empty()
                .open(ShopRoute.defaultHome())
                .open(new ShopRoute.Cart())
                .open(new ShopRoute.Checkout())
                .completeCheckout(receipt);

        assertThat(state.current()).contains(receipt);
        assertThat(state.backTargets()).noneMatch(ShopRoute.Checkout.class::isInstance);
        assertThat(state.back().current()).contains(ShopRoute.defaultHome());
    }

    private static ProductSearchQuery search(String keyword) {
        return new ProductSearchQuery(keyword, null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);
    }

    private static PaymentView payment() {
        return new PaymentView("payment-1", "group-1", "P0001",
                new BigDecimal("12.00"), PaymentStatus.SUCCEEDED,
                PaymentChannel.ALIPAY, Instant.parse("2026-09-01T00:00:00Z"),
                null, 0);
    }
}
