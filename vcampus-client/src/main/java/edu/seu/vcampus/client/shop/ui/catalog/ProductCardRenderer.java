package edu.seu.vcampus.client.shop.ui.catalog;

import edu.seu.vcampus.common.shop.ProductSummary;

import javax.swing.ImageIcon;
import javax.swing.JComponent;

/**
 * 商品卡片图形界面渲染器接口，解耦卡片布局与数据绑定。
 */
@FunctionalInterface
public interface ProductCardRenderer {
    JComponent render(ProductSummary product, ImageIcon image, Runnable openDetail);
}
