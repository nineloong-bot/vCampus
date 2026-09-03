package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartItemView;

import javax.swing.*;
import java.math.BigDecimal;

final class CheckoutItemRow extends JPanel {
    CheckoutItemRow(CartItemView item, Runnable open, ShopUiKit kit) {
        ShopComponentStyle.styleTextComponent(this);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        String base = "checkout-item-" + item.cartItemId();
        setName(base);

        JButton product = kit.navigationButton(base + ".product",
                item.productName() + " · " + item.skuName());
        product.addActionListener(event -> open.run());
        add(product);

        add(named(new JLabel("单价：" + CartItemCard.money(item.displayedUnitPrice())), base + ".unit-price"));
        add(named(new JLabel("数量：" + item.quantity()), base + ".quantity"));
        add(named(new JLabel("小计：" + CartItemCard.money(item.displayedUnitPrice().multiply(BigDecimal.valueOf(item.quantity())))),
                base + ".subtotal"));
    }

    private static <T extends JComponent> T named(T value, String name) {
        value.setName(name);
        return value;
    }
}