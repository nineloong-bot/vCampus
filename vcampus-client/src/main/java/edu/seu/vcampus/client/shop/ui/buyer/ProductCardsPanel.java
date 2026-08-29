package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Displays a navigable list of buyer product summaries. */
public final class ProductCardsPanel extends JPanel {
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final List<String> productNames = new ArrayList<>();
    private final List<String> prices = new ArrayList<>();

    public ProductCardsPanel(ShopNavigator navigator, ShopUiKit uiKit) {
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    public void showProducts(Collection<ProductSummary> products) {
        removeAll();
        productNames.clear();
        prices.clear();
        for (ProductSummary product : products) {
            add(card(product));
            productNames.add(product.productName());
            prices.add(formatPrice(product.minimumPrice()));
        }
        revalidate();
        repaint();
    }

    public List<String> visibleProductNames() { return List.copyOf(productNames); }

    public List<String> visiblePrices() { return List.copyOf(prices); }

    private JPanel card(ProductSummary product) {
        String name = "product-" + product.productId();
        JPanel card = uiKit.productCard(name, new FlowLayout(FlowLayout.LEFT));
        JButton action = new JButton("%s | %s | %s | 销量 %d | %s".formatted(
                product.productName(), product.shopName(), product.category(), product.salesCount(),
                formatPrice(product.minimumPrice())));
        action.setName(name);
        action.addActionListener(event -> navigator.open(new ShopRoute.Product(product.productId())));
        card.add(action);
        return card;
    }

    private static String formatPrice(BigDecimal price) { return "¥" + price.setScale(2) + " 起"; }
}
