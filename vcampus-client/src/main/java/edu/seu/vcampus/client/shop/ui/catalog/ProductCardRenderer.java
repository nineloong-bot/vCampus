package edu.seu.vcampus.client.shop.ui.catalog;

import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.ImageIcon;
import javax.swing.JComponent;

@FunctionalInterface
public interface ProductCardRenderer {
    JComponent render(ProductSummary product, ImageIcon image, Runnable openDetail);
}
