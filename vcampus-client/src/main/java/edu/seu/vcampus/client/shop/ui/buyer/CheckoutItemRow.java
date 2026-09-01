package edu.seu.vcampus.client.shop.ui.buyer;
import edu.seu.vcampus.common.shop.CartItemView;
import javax.swing.*;
import java.math.BigDecimal;
final class CheckoutItemRow extends JPanel {
    CheckoutItemRow(CartItemView item, Runnable open) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS)); String base = "checkout-item-" + item.cartItemId(); setName(base);
        JButton product = new JButton(item.productName() + " · " + item.skuName()); product.setName(base + ".product"); product.addActionListener(e -> open.run()); add(product);
        add(named(new JLabel("单价：" + CartItemCard.money(item.displayedUnitPrice())), base + ".unit-price"));
        add(named(new JLabel("数量：" + item.quantity()), base + ".quantity"));
        add(named(new JLabel("小计：" + CartItemCard.money(item.displayedUnitPrice().multiply(BigDecimal.valueOf(item.quantity())))), base + ".subtotal"));
    }
    private static <T extends JComponent> T named(T value, String name) { value.setName(name); return value; }
}
