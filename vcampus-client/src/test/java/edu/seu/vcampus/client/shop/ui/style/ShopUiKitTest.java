package edu.seu.vcampus.client.shop.ui.style;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.buyer.ProductSearchPanel;
import edu.seu.vcampus.client.shop.ui.buyer.ShopHomePanel;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.ProductSearchQuery;
import edu.seu.vcampus.common.shop.ProductSortMode;
import edu.seu.vcampus.common.shop.ProductSummary;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.LayoutManager;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.flushEdt;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopUiKitTest {
    @Test
    void buyerSourcesDoNotOwnThemeStyling() throws Exception {
        String buyerSources;
        try (Stream<Path> files = Files.walk(Path.of(
                "src/main/java/edu/seu/vcampus/client/shop/ui/buyer"))) {
            buyerSources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> assertDoesNotThrow(() -> Files.readString(path)))
                    .collect(Collectors.joining("\n"));
        }

        assertThat(buyerSources).doesNotContain("java.awt.Color", "new Font", "BorderFactory");
    }

    @Test
    void homeAndResultsDelegateButtonsCardsAndStatesToInjectedKit() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        when(client.home(any())).thenReturn(CompletableFuture.completedFuture(new PageResult<>(List.of(
                new ProductSummary("product-1", "shop-1", "文具店", "签字笔", "文具",
                        new BigDecimal("3.00"), 2, Instant.EPOCH)), 0, 20, 1)));
        RecordingKit kit = new RecordingKit();
        ShopHomePanel panel = onEdt(() -> new ShopHomePanel(client,
                new ShopNavigator(route -> { }), kit, () -> { }));

        onEdt(() -> panel.load());
        flushEdt();

        assertThat(kit.primaryButtons).contains("home.search");
        assertThat(kit.productCards).contains("product-product-1");
        assertThat(kit.states).contains(ShopPageState.LOADING, ShopPageState.NORMAL);
    }

    @Test
    void searchReportsEmptyErrorAndDisconnectedStatesThroughInjectedKit() throws Exception {
        ShopClientPort client = mock(ShopClientPort.class);
        CompletableFuture<PageResult<ProductSummary>> empty = CompletableFuture.completedFuture(
                new PageResult<>(List.of(), 0, 20, 0));
        CompletableFuture<PageResult<ProductSummary>> failed = new CompletableFuture<>();
        CompletableFuture<PageResult<ProductSummary>> expired = new CompletableFuture<>();
        when(client.search(any())).thenReturn(empty, failed, expired);
        RecordingKit kit = new RecordingKit();
        List<String> expiredSignals = new ArrayList<>();
        ProductSearchPanel panel = onEdt(() -> new ProductSearchPanel(client,
                new ShopNavigator(route -> { }), kit, () -> expiredSignals.add("expired")));
        ProductSearchQuery query = new ProductSearchQuery(null, null, null, null,
                ProductSortMode.SALES_DESC, 0, 20);

        onEdt(() -> panel.search(query));
        flushEdt();
        onEdt(() -> panel.search(query));
        failed.completeExceptionally(new IllegalStateException("SHOP_UNAVAILABLE"));
        flushEdt();
        onEdt(() -> panel.search(query));
        expired.completeExceptionally(new IllegalStateException("AUTH_SESSION_EXPIRED"));
        flushEdt();

        assertThat(kit.states).contains(ShopPageState.LOADING, ShopPageState.EMPTY,
                ShopPageState.ERROR, ShopPageState.DISCONNECTED);
        assertThat(expiredSignals).containsExactly("expired");
    }

    private static final class RecordingKit implements ShopUiKit {
        private final List<String> primaryButtons = new ArrayList<>();
        private final List<String> productCards = new ArrayList<>();
        private final EnumSet<ShopPageState> states = EnumSet.noneOf(ShopPageState.class);

        @Override
        public JButton primaryButton(String name, String text) {
            primaryButtons.add(name);
            return named(new JButton(text), name);
        }

        @Override
        public JButton secondaryButton(String name, String text) {
            return named(new JButton(text), name);
        }

        @Override
        public JPanel filterPanel(String name, LayoutManager layout) {
            return named(new JPanel(layout), name);
        }

        @Override
        public JPanel productCard(String name, LayoutManager layout) {
            productCards.add(name);
            return named(new JPanel(layout), name);
        }

        @Override
        public JComponent stateView(String name, ShopPageState state, String message,
                Runnable retry) {
            states.add(state);
            return named(new JLabel(message), name);
        }

        private static <T extends JComponent> T named(T component, String name) {
            component.setName(name);
            return component;
        }
    }
}
