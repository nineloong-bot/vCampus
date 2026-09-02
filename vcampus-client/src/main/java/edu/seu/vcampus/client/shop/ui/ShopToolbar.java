package edu.seu.vcampus.client.shop.ui;

import edu.seu.vcampus.client.shop.ui.navigation.ShopNavigator;
import edu.seu.vcampus.client.shop.ui.navigation.ShopRoute;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Objects;

/** Shared buyer toolbar driven exclusively by navigation and cart models. */
public final class ShopToolbar extends JPanel {
    private final ShopNavigator navigator;
    private final CartCountModel cartCount;
    private final JLabel title = named(new JLabel("校园商城"), "shop.title");
    private final JButton back;
    private final JButton cart;
    private final JButton my;

    public ShopToolbar(ShopNavigator navigator, CartCountModel cartCount, ShopUiKit uiKit,
            Runnable returnHome) {
        super(new BorderLayout(8, 0));
        setName("shop.toolbar");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.cartCount = Objects.requireNonNull(cartCount, "cartCount");
        Objects.requireNonNull(uiKit, "uiKit");
        Objects.requireNonNull(returnHome, "returnHome");
        back = uiKit.secondaryButton("shop.back", "← 返回");
        cart = uiKit.secondaryButton("shop.cart", cartText(cartCount.totalQuantity()));
        my = uiKit.secondaryButton("shop.my", "我的");
        JButton home = uiKit.secondaryButton("shop.return-home", "返回首页");
        JPanel actions = named(new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)),
                "shop.actions");
        actions.add(home); actions.add(my); actions.add(cart);
        add(back, BorderLayout.WEST); add(title, BorderLayout.CENTER); add(actions, BorderLayout.EAST);
        back.addActionListener(event -> navigator.back());
        home.addActionListener(event -> returnHome.run());
        cart.addActionListener(event -> navigator.open(new ShopRoute.Cart()));
        my.addActionListener(event -> navigator.open(new ShopRoute.My()));
        navigator.addListener(this::navigationChanged);
        cartCount.addListener(quantity -> cart.setText(cartText(quantity)));
        refresh();
    }

    private void navigationChanged(ShopRoute ignored) { refresh(); }

    private void refresh() {
        ShopRoute route = navigator.current().orElse(null);
        title.setText(title(route));
        back.setEnabled(navigator.canGoBack());
        cart.setVisible(!(route instanceof ShopRoute.Search));
    }

    private static String title(ShopRoute route) {
        return switch (route) {
            case null -> "校园商城";
            case ShopRoute.Home ignored -> "商城首页";
            case ShopRoute.Search ignored -> "商品搜索";
            case ShopRoute.Product ignored -> "商品详情";
            case ShopRoute.Storefront ignored -> "店铺";
            case ShopRoute.Cart ignored -> "购物车";
            case ShopRoute.Checkout ignored -> "确认订单";
            case ShopRoute.PaymentResult ignored -> "支付结果";
            case ShopRoute.My ignored -> "我的商城";
            case ShopRoute.SellerApplication ignored -> "开店申请";
            case ShopRoute.SellerWorkspace ignored -> "卖家工作区";
            case ShopRoute.AdminWorkspace ignored -> "商城管理";
        };
    }

    private static String cartText(int quantity) { return "购物车（" + quantity + "）"; }
    private static <T extends java.awt.Component> T named(T component, String name) {
        component.setName(name); return component;
    }
}
