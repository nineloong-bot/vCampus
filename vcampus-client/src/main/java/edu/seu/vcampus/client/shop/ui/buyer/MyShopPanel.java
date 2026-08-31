package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.shop.PaidOrderItemView;
import edu.seu.vcampus.common.shop.PaidOrderView;
import edu.seu.vcampus.common.user.UserView;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Read-only buyer identity and paid-order history. */
public final class MyShopPanel extends JPanel {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PAID_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(DISPLAY_ZONE);

    private final ShopClientPort client;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private boolean disposed;
    private boolean disconnected;

    public MyShopPanel(UserView user, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        add(identity(Objects.requireNonNull(user, "user")), BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        showState(ShopPageState.INITIAL, "", null);
    }

    public void load() {
        if (disposed || disconnected) {
            return;
        }
        long request = requests.begin();
        showState(ShopPageState.LOADING, "加载已支付订单…", null);
        client.getPaidOrders().whenComplete((history, failure) ->
                finish(request, history, failure));
    }

    public void disposePage() {
        if (disposed) {
            return;
        }
        disposed = true;
        requests.dispose();
    }

    private JPanel identity(UserView user) {
        JPanel panel = uiKit.filterPanel("my.identity", new GridLayout(4, 2, 8, 4));
        addIdentity(panel, "my.user-id", "用户编号：", user.userId(),
                "当前登录用户的编号");
        addIdentity(panel, "my.login-id", "登录名：", user.loginId(),
                "当前登录用户的登录名");
        addIdentity(panel, "my.role", "角色：", user.role().name(),
                "当前登录用户的角色");
        addIdentity(panel, "my.account-status", "账户状态：", user.accountStatus().name(),
                "当前登录用户的账户状态");
        return panel;
    }

    private void finish(long request, PaidOrderHistory history, Throwable failure) {
        SwingUtilities.invokeLater(() -> {
            if (!requests.accepts(request)) {
                return;
            }
            if (failure != null) {
                showFailure(failure);
                return;
            }
            render(history);
        });
    }

    private void render(PaidOrderHistory history) {
        if (history.orders().isEmpty()) {
            showState(ShopPageState.EMPTY, "暂无已支付订单", this::load);
            return;
        }
        content.removeAll();
        JPanel normal = uiKit.filterPanel("my.normal", new BorderLayout(4, 4));
        normal.add(uiKit.stateView("my.state", ShopPageState.NORMAL, "", null),
                BorderLayout.NORTH);
        JPanel orders = uiKit.filterPanel("my.orders", new FlowLayout());
        orders.setLayout(new BoxLayout(orders, BoxLayout.Y_AXIS));
        for (PaidOrderView order : history.orders()) {
            orders.add(order(order));
        }
        JScrollPane scroll = named(new JScrollPane(orders,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "my.orders.scroll");
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        normal.add(scroll, BorderLayout.CENTER);
        content.add(normal, BorderLayout.CENTER);
        refresh();
    }

    private JPanel order(PaidOrderView order) {
        String suffix = order.orderId();
        JPanel card = uiKit.spacedProductCard("my.order." + suffix,
                new BorderLayout(4, 4), 8);
        JPanel summary = uiKit.filterPanel("my.order.summary." + suffix,
                new FlowLayout(FlowLayout.LEFT));
        summary.add(named(new JLabel(order.orderNumber()), "my.order.number." + suffix));
        summary.add(named(new JLabel(order.shopName()), "my.order.shop." + suffix));
        JButton toggle = uiKit.secondaryButton("my.order.toggle." + suffix, "展开");
        summary.add(toggle);

        JPanel details = uiKit.filterPanel("my.order.details." + suffix,
                new GridLayout(0, 1, 0, 2));
        for (PaidOrderItemView item : order.items()) {
            details.add(named(new JLabel(
                    "商品 %s（%s） | SKU %s（%s） | 数量 %d | 单价 ¥%s | 行金额 ¥%s"
                    .formatted(item.productName(), item.productId(), item.skuName(), item.skuId(),
                            item.quantity(), money(item.unitPrice()),
                            money(item.lineAmount()))),
                    "my.order.item." + suffix + "." + item.skuId()));
        }
        details.add(named(new JLabel("总额 ¥" + money(order.totalAmount())),
                "my.order.total." + suffix));
        details.add(named(new JLabel("支付时间 " + paidAt(order.paidAt())),
                "my.order.paid-at." + suffix));
        details.add(named(new JLabel("状态 " + order.status().name()),
                "my.order.status." + suffix));
        details.setVisible(false);
        toggle.addActionListener(event -> {
            boolean expanded = !details.isVisible();
            details.setVisible(expanded);
            toggle.setText(expanded ? "收起" : "展开");
            details.revalidate();
            card.revalidate();
            if (card.getParent() instanceof JPanel orders) {
                orders.invalidate();
                orders.revalidate();
                orders.repaint();
            }
            content.revalidate();
            revalidate();
            repaint();
        });
        card.add(summary, BorderLayout.NORTH);
        card.add(details, BorderLayout.CENTER);
        return card;
    }

    private void showFailure(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        if (ShopUiErrors.sessionExpired(code)) {
            disconnected = true;
            requests.dispose();
            showState(ShopPageState.DISCONNECTED, code, null);
            sessionExpired.run();
            return;
        }
        showState(ShopPageState.ERROR, code, this::load);
    }

    private void showState(ShopPageState state, String message, Runnable retry) {
        content.removeAll();
        content.add(uiKit.stateView("my.state", state, message, retry), BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        content.revalidate();
        content.repaint();
    }

    private static void addIdentity(JPanel panel, String name, String labelText,
            String valueText, String description) {
        JLabel value = named(new JLabel(valueText), name);
        value.getAccessibleContext().setAccessibleName(labelText.replace("：", ""));
        value.getAccessibleContext().setAccessibleDescription(description);
        JLabel label = named(new JLabel(labelText), name + ".label");
        label.setLabelFor(value);
        panel.add(label);
        panel.add(value);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String paidAt(Instant instant) {
        return PAID_AT_FORMAT.format(instant) + " " + DISPLAY_ZONE;
    }

    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
