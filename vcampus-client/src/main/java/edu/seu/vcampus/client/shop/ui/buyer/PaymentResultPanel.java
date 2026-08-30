package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.HomeProductQuery;
import edu.seu.vcampus.common.shop.PaymentView;
import edu.seu.vcampus.common.shop.ProductSortMode;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;

/** Final payment receipt and safe navigation exits. */
public final class PaymentResultPanel extends JPanel {
    private final ShopNavigator navigator;
    private final ShopUiKit uiKit;
    private final PaymentView payment;

    public PaymentResultPanel(ShopNavigator navigator, ShopUiKit uiKit, PaymentView payment) {
        super(new BorderLayout(8, 8));
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.payment = Objects.requireNonNull(payment, "payment");
        render();
    }

    public void openHome() {
        navigator.reset(new ShopRoute.Home(new HomeProductQuery(null, null,
                ProductSortMode.SALES_DESC, 0, 20)));
    }
    public void openPaidOrders() { navigator.reset(new ShopRoute.My()); }

    private void render() {
        JPanel normal = uiKit.filterPanel("payment-result.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("payment-result.state", ShopPageState.NORMAL, "", null), BorderLayout.NORTH);
        JPanel details = uiKit.filterPanel("payment-result.details", new FlowLayout(FlowLayout.LEFT));
        details.add(named(new JLabel(payment.paymentNumber()), "payment-number"));
        details.add(named(new JLabel("¥" + payment.amount().toPlainString()), "payment-amount"));
        details.add(named(new JLabel(String.valueOf(payment.successfulChannel())), "payment-channel"));
        details.add(named(new JLabel(payment.status().name()), "payment-status"));
        JButton home = uiKit.primaryButton("payment-result.home", "继续购物");
        JButton orders = uiKit.secondaryButton("payment-result.orders", "查看已支付订单");
        home.addActionListener(event -> openHome()); orders.addActionListener(event -> openPaidOrders());
        details.add(home); details.add(orders); normal.add(details, BorderLayout.CENTER); add(normal, BorderLayout.CENTER);
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name); return component;
    }
}
