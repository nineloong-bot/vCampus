package edu.seu.vcampus.client.shop.ui.buyer;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.CartItemView;
import javax.swing.*;
import java.awt.*;
import java.math.*;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
final class CartItemCard extends JPanel {
    final JButton update; final JButton remove; final JCheckBox selected;
    CartItemCard(CartItemView item, ShopUiKit kit, Runnable open, IntConsumer updateQuantity,
            Runnable removeItem, boolean isSelected, Consumer<Boolean> selectionChanged) {
        super(); setName("cart-item-" + item.cartItemId()); setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        String base = "cart-item-" + item.cartItemId();
        JButton product = kit.secondaryButton(base + ".product", item.productName() + " · " + item.skuName());
        product.addActionListener(event -> open.run()); add(product);
        selected = named(new JCheckBox("选择", isSelected), "cart.select-" + item.cartItemId());
        selected.addActionListener(event -> selectionChanged.accept(selected.isSelected()));
        add(selected);
        add(named(new JLabel("单价：" + money(item.displayedUnitPrice())), base + ".unit-price"));
        add(named(new JLabel("数量：" + item.quantity()), base + ".quantity"));
        add(named(new JLabel("小计：" + money(item.displayedUnitPrice().multiply(BigDecimal.valueOf(item.quantity())))), base + ".subtotal"));
        JSpinner quantity = named(new JSpinner(new SpinnerNumberModel(item.quantity(), 1, Integer.MAX_VALUE, 1)), base + ".quantity-input");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT)); actions.add(quantity);
        update = kit.secondaryButton("cart.update-" + item.cartItemId(), "更新");
        remove = kit.secondaryButton("cart.remove-" + item.cartItemId(), "移除");
        update.addActionListener(event -> updateQuantity.accept((Integer) quantity.getValue())); remove.addActionListener(event -> removeItem.run());
        actions.add(update); actions.add(remove); add(actions);
    }
    static String money(BigDecimal amount) { return "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static <T extends JComponent> T named(T value, String name) { value.setName(name); return value; }
}
