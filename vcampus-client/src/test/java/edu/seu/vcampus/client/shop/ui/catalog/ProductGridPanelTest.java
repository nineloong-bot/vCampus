package edu.seu.vcampus.client.shop.ui.catalog;

import edu.seu.vcampus.client.shop.ShopSwingTestSupport;
import edu.seu.vcampus.common.shop.ProductSummary;
import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.component;
import static edu.seu.vcampus.client.shop.ShopSwingTestSupport.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

class ProductGridPanelTest {
    @Test
    void rendersOneClickableCardPerProductType() throws Exception {
        List<String> opened = new ArrayList<>();
        ProductGridPanel grid = onEdt(() -> new ProductGridPanel(images(), defaultRenderer(), opened::add));

        onEdt(() -> grid.showProducts(List.of(summary("p1", "中性笔", "2.80"),
                summary("p2", "笔记本", "6.90"))));
        onEdt(() -> component(grid, "product-card.p1", JButton.class).doClick());

        assertThat(grid.visibleProductNames()).containsExactly("中性笔", "笔记本");
        assertThat(opened).containsExactly("p1");
    }

    @Test
    void injectedRendererCanChangeCardWithoutChangingNavigation() throws Exception {
        List<String> opened = new ArrayList<>();
        ProductCardRenderer renderer = (product, image, open) -> {
            JButton card = new JButton(product.productName());
            card.setName("custom-card." + product.productId());
            card.addActionListener(event -> open.run());
            return card;
        };
        ProductGridPanel grid = onEdt(() -> new ProductGridPanel(images(), renderer, opened::add));

        onEdt(() -> grid.showProducts(List.of(summary("p1", "中性笔", "2.80"))));
        onEdt(() -> component(grid, "custom-card.p1", JButton.class).doClick());

        assertThat(opened).containsExactly("p1");
    }

    @Test
    void rendersCardsBeforeAnAsynchronousImageFinishesLoading() throws Exception {
        CompletableFuture<ImageIcon> image = new CompletableFuture<>();
        ProductGridPanel grid = onEdt(() -> new ProductGridPanel(
                (url, category, target) -> image, defaultRenderer(), id -> { }));

        onEdt(() -> grid.showProducts(List.of(summary("p1", "中性笔", "2.80"))));

        assertThat(component(grid, "product-card.p1", JButton.class)).isNotNull();
    }

    @Test
    void preferredHeightIncludesWrappedRowsAtTheAvailableWidth() throws Exception {
        ProductCardRenderer cards = (product, image, open) -> {
            JButton card = new JButton(product.productName());
            card.setPreferredSize(new Dimension(160, 180));
            return card;
        };
        ProductGridPanel grid = onEdt(() -> new ProductGridPanel(images(), cards, id -> { }));

        onEdt(() -> {
            grid.setSize(380, 1000);
            grid.showProducts(List.of(
                    summary("p1", "一", "1.00"), summary("p2", "二", "2.00"),
                    summary("p3", "三", "3.00"), summary("p4", "四", "4.00"),
                    summary("p5", "五", "5.00")));
        });

        assertThat(onEdt(() -> grid.getPreferredSize().height)).isGreaterThan(500);
    }

    @Test
    void firstPreferredHeightUsesLaidOutAncestorWidthBeforeGridReceivesBounds() throws Exception {
        ProductCardRenderer cards = (product, image, open) -> {
            JButton card = new JButton(product.productName());
            card.setPreferredSize(new Dimension(160, 180));
            return card;
        };
        ProductGridPanel grid = onEdt(() -> new ProductGridPanel(images(), cards, id -> { }));
        JPanel viewportContent = onEdt(() -> {
            JPanel wrapper = new JPanel(new BorderLayout());
            wrapper.setSize(900, 700);
            wrapper.add(grid, BorderLayout.CENTER);
            return wrapper;
        });

        onEdt(() -> grid.showProducts(java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> summary("p" + index, "商品" + index, "1.00"))
                .toList()));

        assertThat(onEdt(grid::getWidth)).isZero();
        assertThat(onEdt(viewportContent::getWidth)).isEqualTo(900);
        assertThat(onEdt(() -> grid.getPreferredSize().height)).isGreaterThan(900);
    }

    @Test
    void defaultLayoutAt1280PxRendersThreeToFourColumnsPerRow() throws Exception {
        ProductCardRenderer cards = (product, image, open) -> {
            JButton card = new JButton(product.productName());
            card.setPreferredSize(new Dimension(300, 180));
            return card;
        };
        ProductGridPanel grid = onEdt(() -> new ProductGridPanel(images(), cards, id -> { }));
        onEdt(() -> {
            grid.setSize(1280, 900);
            grid.showProducts(java.util.stream.IntStream.range(0, 12)
                    .mapToObj(index -> summary("p" + index, "商品" + index, "1.00"))
                    .toList());
        });

        int renderedColumns = onEdt(() -> {
            grid.doLayout();
            int firstRowY = grid.getComponent(0).getY();
            int columnCount = 0;
            for (java.awt.Component component : grid.getComponents()) {
                if (component.getY() == firstRowY) columnCount++;
            }
            return columnCount;
        });

        assertThat(renderedColumns).isBetween(3, 4);
        assertThat(grid.getPreferredSize().width).isGreaterThan(800);
    }

    private static ProductImageLoader images() {
        return (url, category, target) -> CompletableFuture.completedFuture(new ImageIcon(
                new BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_ARGB)));
    }

    private static ProductCardRenderer defaultRenderer() {
        return (product, image, open) -> {
            JButton card = new JButton(product.productName());
            card.setName("product-card." + product.productId());
            card.addActionListener(event -> open.run());
            return card;
        };
    }

    private static ProductSummary summary(String id, String name, String price) {
        return new ProductSummary(id, "shop", "店", name, "文具", null,
                new BigDecimal(price), 0, Instant.EPOCH);
    }
}
