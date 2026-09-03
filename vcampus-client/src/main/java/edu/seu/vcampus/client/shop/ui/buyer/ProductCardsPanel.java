package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.catalog.BuiltinProductImageLoader;
import edu.seu.vcampus.client.shop.ui.catalog.ProductCardRenderer;
import edu.seu.vcampus.client.shop.ui.catalog.ProductGridPanel;
import edu.seu.vcampus.common.shop.ProductSummary;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

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
    private final ProductCardContext context;

    public ProductCardsPanel(ShopNavigator navigator, ShopUiKit uiKit) {
        this(navigator, uiKit, ProductCardContext.SEARCH);
    }

    public ProductCardsPanel(ShopNavigator navigator, ShopUiKit uiKit, ProductCardContext context) {
        ShopNavigator routes = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.context = Objects.requireNonNull(context, "context");
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
        JLabel productName = named(new JLabel(product.productName()), name + ".name");
        productName.setFont(UiTypography.BODY_BOLD);
        card.add(productName);
        if (context.showShopName()) {
            JLabel shopName = named(new JLabel(product.shopName()), name + ".shop");
            shopName.setFont(UiTypography.CAPTION);
            card.add(shopName);
        }
        JLabel category = named(new JLabel(product.category()), name + ".category");
        category.setFont(UiTypography.CAPTION);
        card.add(category);
        JLabel price = named(new JLabel(formatPrice(product.minimumPrice())), name + ".price");
        price.setFont(UiTypography.BODY_BOLD);
        card.add(price);
        JLabel sales = named(new JLabel("销量 " + product.salesCount()), name + ".sales");
        sales.setFont(UiTypography.CAPTION);
        card.add(sales);
        card.setFocusable(true);
        card.getAccessibleContext().setAccessibleName(product.productName() + " " + formatPrice(product.minimumPrice()));
        java.awt.event.MouseAdapter activate = new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) { open.run(); }
        };
        installActivation(card, activate);
        card.getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "open");
        card.getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "open");
        card.getActionMap().put("open", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { open.run(); }
        });
        return card;
    }

    private static void installActivation(java.awt.Container root, java.awt.event.MouseAdapter activation) {
        root.addMouseListener(activation);
        for (java.awt.Component child : root.getComponents()) {
            child.addMouseListener(activation);
            if (child instanceof java.awt.Container nested) installActivation(nested, activation);
        }
    }

    private static String formatPrice(BigDecimal price) { return "¥" + price.setScale(2) + " 起"; }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name); return component;
    }
}
