package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.math.RoundingMode;
import java.util.Objects;

/** Read-only seller order cards using immutable checkout snapshots. */
public final class SellerOrdersPanel extends JPanel {
    private final SellerShopClientPort port;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final JPanel content = new JPanel();
    private boolean disposed;

    public SellerOrdersPanel(SellerShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout());
        ShopComponentStyle.pagePanel(this);
        this.port = Objects.requireNonNull(port, "port"); this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        ShopComponentStyle.styleTextComponent(content);
        add(ShopComponentStyle.styleScrollPane(new JScrollPane(content)));
    }
    public void load() {
        if (disposed) return; long request = requests.begin();
        port.getOwnedOrders(new SellerOrderQuery(null, 0, 50)).whenComplete((history, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    content.removeAll(); for (SellerOrderView order : history.orders()) content.add(card(order));
                    content.revalidate(); content.repaint();
                }));
    }
    public void disposePage() { disposed = true; requests.dispose(); }
    private JPanel card(SellerOrderView order) {
        JPanel card = uiKit.spacedProductCard("seller.order." + order.orderId(),
                new BorderLayout(4, 4), 8);
        JButton toggle = uiKit.secondaryButton("seller.order.toggle." + order.orderId(), "展开");
        JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        header.add(new JLabel(order.orderNumber() + " | 买家 " + order.buyerUserId()
                + " | ¥" + money(order.totalAmount()))); header.add(toggle);
        JPanel detail = named(new JPanel(new java.awt.GridLayout(0, 1)),
                "seller.order.details." + order.orderId());
        for (SellerOrderItemView item : order.items()) detail.add(named(new JLabel(
                item.productName() + " / " + item.skuName() + " × " + item.quantity()
                        + " = ¥" + money(item.lineAmount())),
                "seller.order.item." + order.orderId() + "." + item.skuId()));
        detail.setVisible(false); toggle.addActionListener(event -> detail.setVisible(!detail.isVisible()));
        card.add(header, BorderLayout.NORTH); card.add(detail, BorderLayout.CENTER); return card;
    }
    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure); content.removeAll();
        content.add(new JLabel(ShopUiErrors.message(code)));
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }
    private static String money(java.math.BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
