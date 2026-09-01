package edu.seu.vcampus.client.shop.ui.buyer;

import edu.seu.vcampus.client.shop.service.ShopClientPort;
import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopPageState;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.PaidOrderHistory;
import edu.seu.vcampus.common.shop.PaidOrderItemView;
import edu.seu.vcampus.common.shop.PaidOrderView;
import edu.seu.vcampus.common.shop.SellerApplicationStatus;
import edu.seu.vcampus.common.shop.SellerApplicationView;
import edu.seu.vcampus.common.user.UserRole;
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

/** Shop-specific actions and paid-order history for the signed-in user. */
public final class MyShopPanel extends JPanel {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PAID_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(DISPLAY_ZONE);

    private final ShopClientPort client;
    private final ShopUiKit uiKit;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final LatestRequest businessRequests = new LatestRequest();
    private final JPanel content = new JPanel(new BorderLayout());
    private final UserRole role;
    private final SellerShopClientPort seller;
    private final ShopNavigator navigator;
    private final JButton businessAction;
    private boolean disposed;
    private boolean disconnected;

    public MyShopPanel(UserView user, ShopClientPort client, ShopUiKit uiKit,
            Runnable sessionExpired) {
        this(user, client, null, null, uiKit, sessionExpired);
    }

    public MyShopPanel(UserView user, ShopClientPort client, SellerShopClientPort seller,
            ShopNavigator navigator, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.client = Objects.requireNonNull(client, "client");
        this.uiKit = Objects.requireNonNull(uiKit, "uiKit");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        UserView activeUser = Objects.requireNonNull(user, "user");
        role = activeUser.role();
        this.seller = seller;
        this.navigator = navigator;
        businessAction = uiKit.primaryButton("my.business.action", businessLabel());
        businessAction.addActionListener(event -> openBusinessPage());
        JPanel north = uiKit.filterPanel("my.header", new BorderLayout(8, 8));
        north.add(named(new JLabel("商城个人中心"), "my.title"), BorderLayout.CENTER);
        if (navigator != null && (role == UserRole.ADMIN || seller != null)) {
            north.add(businessAction, BorderLayout.EAST);
        }
        add(north, BorderLayout.NORTH);
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
        loadBusinessAction();
    }

    public void disposePage() {
        if (disposed) {
            return;
        }
        disposed = true;
        requests.dispose();
        businessRequests.dispose();
    }

    private String businessLabel() {
        if (role == UserRole.ADMIN) return "商城管理";
        return seller == null ? "" : "加载申请状态…";
    }

    private void loadBusinessAction() {
        if (role == UserRole.ADMIN || seller == null || disposed) return;
        businessAction.setEnabled(false);
        long request = businessRequests.begin();
        seller.getMyApplication().whenComplete((application, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!businessRequests.accepts(request)) return;
                    if (failure != null) {
                        String code = ShopUiErrors.code(failure);
                        businessAction.setText(code);
                        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
                        return;
                    }
                    businessAction.setText(application.map(MyShopPanel::applicationLabel)
                            .orElse("申请开店"));
                    businessAction.setEnabled(true);
                    businessAction.putClientProperty("shop.application.status",
                            application.map(SellerApplicationView::status).orElse(null));
                }));
    }

    private static String applicationLabel(SellerApplicationView application) {
        return switch (application.status()) {
            case DRAFT -> "继续申请";
            case PENDING -> "查看申请";
            case REJECTED -> "修改并重新提交";
            case APPROVED -> "进入卖家工作区";
        };
    }

    private void openBusinessPage() {
        if (navigator == null) return;
        if (role == UserRole.ADMIN) {
            navigator.open(new ShopRoute.AdminWorkspace());
            return;
        }
        Object status = businessAction.getClientProperty("shop.application.status");
        navigator.open(status == SellerApplicationStatus.APPROVED
                ? new ShopRoute.SellerWorkspace()
                : new ShopRoute.SellerApplication());
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
