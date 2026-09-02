package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ShopNavigatorTest {
    @ParameterizedTest
    @MethodSource("utilitySequences")
    void utilityPagesReturnOnceToTheirContentAnchor(List<ShopRoute> sequence) {
        ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));
        ShopNavigator navigator = new ShopNavigator(route -> { });
        navigator.open(home);
        sequence.forEach(navigator::open);

        navigator.back();

        assertThat(navigator.current()).contains(home);
        assertThat(navigator.history()).isEmpty();
    }

    static Stream<List<ShopRoute>> utilitySequences() {
        return Stream.of(
                List.of(new ShopRoute.My()),
                List.of(new ShopRoute.Cart()),
                List.of(new ShopRoute.My(), new ShopRoute.Cart()),
                List.of(new ShopRoute.Cart(), new ShopRoute.My()),
                List.of(new ShopRoute.My(), new ShopRoute.Cart(), new ShopRoute.My()),
                List.of(new ShopRoute.Cart(), new ShopRoute.My(), new ShopRoute.My()));
    }

    @Test
    void leaveGuardCommitsNoNavigationStateUntilProceedRuns() {
        AtomicReference<Runnable> held = new AtomicReference<>();
        ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));
        ShopRoute product = new ShopRoute.Product("p1");
        ShopNavigator navigator = new ShopNavigator(route -> { });
        navigator.open(home);
        navigator.setLeaveGuard(held::set);

        navigator.open(product);

        assertThat(navigator.current()).contains(home);
        assertThat(navigator.history()).isEmpty();

        held.get().run();

        assertThat(navigator.current()).contains(product);
        assertThat(navigator.history()).containsExactly(home);
    }

    @Test
    void startsWithoutCurrentRouteOrHistoryAndBackIsSafe() {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);

        assertThat(navigator.current()).isEmpty();
        assertThat(navigator.history()).isEmpty();

        navigator.back();

        assertThat(rendered).isEmpty();
    }

    @Test
    void repeatedProductsReplaceOneDetailLayerAndKeepHomeReachable() {
        ShopNavigator navigator = new ShopNavigator(route -> { });
        navigator.open(ShopRoute.defaultHome());
        IntStream.range(0, 100).forEach(index ->
                navigator.open(new ShopRoute.Product("product-" + index)));

        assertThat(navigator.history()).containsExactly(ShopRoute.defaultHome());
        navigator.back();
        assertThat(navigator.current()).contains(ShopRoute.defaultHome());
        assertThat(navigator.canGoBack()).isFalse();
    }

    @Test
    void resetToDefaultHomeWaitsForTheActiveLeaveGuard() {
        AtomicReference<Runnable> held = new AtomicReference<>();
        ShopNavigator navigator = new ShopNavigator(route -> { });
        navigator.open(ShopRoute.defaultHome());
        navigator.open(new ShopRoute.SellerApplication());
        navigator.setLeaveGuard(held::set);

        navigator.resetToDefaultHome();

        assertThat(navigator.current()).contains(new ShopRoute.SellerApplication());
        held.get().run();
        assertThat(navigator.current()).contains(ShopRoute.defaultHome());
        assertThat(navigator.history()).isEmpty();
    }

    @Test
    void sameRouteCapturesLiveStateBeforeRepublishingWithoutGrowingHistory() {
        HomeProductQuery query = new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 2, 20);
        ShopRoute.Home requested = new ShopRoute.Home(query);
        ShopRoute.Home captured = new ShopRoute.Home(new HomeViewState(query, 275));
        List<ShopRoute> rendered = new ArrayList<>();
        List<ShopRoute> changes = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(new ShopRouteHost() {
            @Override public ShopRoute capture(ShopRoute route) { return captured; }
            @Override public void render(ShopRoute route) { rendered.add(route); }
        });
        navigator.addListener(changes::add);

        navigator.open(requested);
        navigator.open(requested);

        assertThat(navigator.current()).contains(captured);
        assertThat(navigator.history()).isEmpty();
        assertThat(rendered).containsExactly(requested, captured);
        assertThat(changes).containsExactly(requested, captured);
    }

    @Test
    void backRestoresNewestHistoryWithoutReaddingDepartedRoute() {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);
        ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));
        ShopRoute search = new ShopRoute.Search(new ProductSearchQuery(
                "笔", "文具", null, null, ProductSortMode.PRICE_DESC, 2, 10));
        ShopRoute product = new ShopRoute.Product("product-1");

        navigator.open(home);
        navigator.open(search);
        navigator.open(product);
        navigator.back();
        navigator.back();
        navigator.back();

        assertThat(navigator.current()).contains(home);
        assertThat(navigator.history()).isEmpty();
        assertThat(rendered).containsExactly(home, search, product, search, home);
    }

    @Test
    void retainsCompleteQueryObjectsAndRendersAfterStateChange() {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);
        HomeProductQuery query = new HomeProductQuery(null, null,
                ProductSortMode.PRICE_DESC, 3, 7);
        ShopRoute route = new ShopRoute.Home(query);

        navigator.open(route);

        assertThat(navigator.current()).contains(route);
        assertThat(((ShopRoute.Home) navigator.current().orElseThrow()).query())
                .isSameAs(query);
        assertThat(rendered).containsExactly(route);
    }

    @Test
    void restoresTheCompleteSearchQueryWhenGoingBack() {
        ProductSearchQuery query = new ProductSearchQuery("笔", "文具", null,
                null, ProductSortMode.PRICE_DESC, 2, 10);
        ShopRoute search = new ShopRoute.Search(query);
        ShopNavigator navigator = new ShopNavigator(route -> { });

        navigator.open(search);
        navigator.open(new ShopRoute.Product("product-1"));
        navigator.back();

        assertThat(navigator.current()).contains(search);
        assertThat(((ShopRoute.Search) navigator.current().orElseThrow()).query())
                .isSameAs(query);
    }

    @Test
    void updatesStateBeforeCallingHostRender() {
        ShopNavigator[] holder = new ShopNavigator[1];
        List<ShopRoute> observedCurrent = new ArrayList<>();
        List<List<ShopRoute>> observedHistory = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(route -> {
            observedCurrent.add(holder[0].current().orElseThrow());
            observedHistory.add(holder[0].history());
        });
        holder[0] = navigator;
        ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));
        ShopRoute product = new ShopRoute.Product("product-1");

        navigator.open(home);
        navigator.open(product);
        navigator.back();

        assertThat(observedCurrent).containsExactly(home, product, home);
        assertThat(observedHistory).containsExactly(List.of(), List.of(home), List.of());
    }

    @Test
    void opensOrdersWithHomeHistoryBeforeRenderingTheTarget() {
        ShopNavigator[] holder = new ShopNavigator[1];
        List<List<ShopRoute>> historySeenByHost = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(route ->
                historySeenByHost.add(holder[0].history()));
        holder[0] = navigator;
        ShopRoute.Home home = new ShopRoute.Home(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));

        navigator.openFromRoot(home, new ShopRoute.My());

        assertThat(navigator.current()).contains(new ShopRoute.My());
        assertThat(navigator.canGoBack()).isTrue();
        assertThat(navigator.history()).containsExactly(home);
        assertThat(historySeenByHost).containsExactly(List.of(home));
    }
}
