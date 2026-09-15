package edu.seu.vcampus.client.shop.commerce;

import edu.seu.vcampus.common.shop.catalog.CatalogDtos.Product;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogFidelityTest {
    @Test void selectedProductHasVisibleSelectionCircleAndPrototypeArtHeight() {
        Product product = new Product("p", "s", "文具店", "笔记本", "", "", "book",
                "PUBLISHED", "sku", false, 3, List.of());
        Component tile = new ProductTile().getListCellRendererComponent(new JList<>(), product, 0, true, false);
        assertThat(find(tile, "product.selection")).isInstanceOf(JLabel.class);
        assertThat(((JLabel) find(tile, "product.selection")).getText()).isEqualTo("✓");
        assertThat(find(tile, "product.art").getPreferredSize().height).isEqualTo(146);
    }
    private Component find(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
