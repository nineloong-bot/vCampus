package edu.seu.vcampus.client.shop.ui.navigation;

import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ShopNavigatorTest {
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
    void ignoresCurrentRouteAndBoundsRestorableHistory() {
        List<ShopRoute> rendered = new ArrayList<>();
        ShopNavigator navigator = new ShopNavigator(rendered::add);
        ProductSearchQuery pens = new ProductSearchQuery("笔", null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);
        ShopRoute home = new ShopRoute.Home(new HomeProductQuery(
                null, null, ProductSortMode.SALES_DESC, 0, 20));
        ShopRoute search = new ShopRoute.Search(pens);

        navigator.open(home);
        navigator.open(search);
        navigator.open(new ShopRoute.Product("product-1"));
        navigator.open(new ShopRoute.Product("product-1"));
        IntStream.range(2, 24).forEach(i ->
                navigator.open(new ShopRoute.Product("product-" + i)));

        assertThat(navigator.history()).hasSize(20);
        assertThat(navigator.history()).doesNotContain(home);
        assertThat(rendered).doesNotHaveDuplicates();
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
}
