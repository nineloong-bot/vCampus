package edu.seu.vcampus.client.shop.ui.catalog;

import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.ImageIcon;
import javax.swing.JButton;

/** Default visual card; navigation remains supplied by the caller. */
public final class DefaultProductCardRenderer implements ProductCardRenderer {
    @Override
    public JButton render(ProductSummary product, ImageIcon image, Runnable openDetail) {
        JButton card = new JButton("%s | ¥%s 起".formatted(product.productName(),
                product.minimumPrice().setScale(2)));
        card.setName("product-card." + product.productId());
        card.setIcon(image);
        card.setHorizontalTextPosition(JButton.CENTER);
        card.setVerticalTextPosition(JButton.BOTTOM);
        card.addActionListener(event -> openDetail.run());
        return card;
    }
}
