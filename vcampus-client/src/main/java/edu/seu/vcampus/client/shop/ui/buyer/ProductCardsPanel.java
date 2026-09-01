package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.catalog.BuiltinProductImageLoader;
import edu.seu.vcampus.client.shop.ui.catalog.ProductCardRenderer;
import edu.seu.vcampus.client.shop.ui.catalog.ProductGridPanel;
import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.JButton;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Displays a navigable list of buyer product summaries. */
public final class ProductCardsPanel extends JPanel {
    private final ShopUiKit uiKit;
    private final ProductGridPanel grid;
    private final List<String> productNames = new ArrayList<>();
    private final List<String> prices = new ArrayList<>();

    public ProductCardsPanel(ShopNavigator navigator, ShopUiKit uiKit) {
        ShopNavigator routes = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        grid = new ProductGridPanel(new BuiltinProductImageLoader(), renderer(),
                productId -> routes.open(new ShopRoute.Product(productId)));
        setLayout(new java.awt.BorderLayout());
        add(grid, java.awt.BorderLayout.CENTER);
    }

    public void showProducts(Collection<ProductSummary> products) {
        productNames.clear();
        prices.clear();
        for (ProductSummary product : products) {
            productNames.add(product.productName());
            prices.add(formatPrice(product.minimumPrice()));
        }
        grid.showProducts(List.copyOf(products));
        revalidate();
        repaint();
    }

    public List<String> visibleProductNames() { return List.copyOf(productNames); }

    public List<String> visiblePrices() { return List.copyOf(prices); }

    private ProductCardRenderer renderer() {
        return (product, image, open) -> card(product, image, open);
    }

    private JPanel card(ProductSummary product, ImageIcon image, Runnable open) {
        String name = "product-" + product.productId();
        JPanel card = uiKit.productCard(name, new java.awt.BorderLayout());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel cover = named(new JLabel(image), name + ".image");
        cover.setPreferredSize(new Dimension(160, 110));
        cover.setMinimumSize(new Dimension(160, 110));
        cover.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        cover.setAlignmentX(CENTER_ALIGNMENT);
        card.add(cover);
        card.add(named(new JLabel(product.productName()), name + ".name"));
        card.add(named(new JLabel(product.shopName()), name + ".shop"));
        card.add(named(new JLabel(product.category()), name + ".category"));
        card.add(named(new JLabel(formatPrice(product.minimumPrice())), name + ".price"));
        card.add(named(new JLabel("销量 " + product.salesCount()), name + ".sales"));
        JButton action = uiKit.secondaryButton(name + ".open",
                product.productName() + " | " + formatPrice(product.minimumPrice()));
        action.setName(name);
        action.addActionListener(event -> open.run());
        card.add(action);
        return card;
    }

    private static String formatPrice(BigDecimal price) { return "¥" + price.setScale(2) + " 起"; }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name); return component;
    }
}
