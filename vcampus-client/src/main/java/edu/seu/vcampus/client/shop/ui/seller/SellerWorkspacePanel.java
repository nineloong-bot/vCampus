package edu.seu.vcampus.client.shop.ui.seller;

import edu.seu.vcampus.client.shop.service.SellerShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;

import javax.swing.*;
import java.awt.BorderLayout;

/** Replaceable seller workspace shell composed from profile, product, and order panels. */
public final class SellerWorkspacePanel extends JPanel {
    private final ShopProfilePanel profile;
    private final ProductManagementPanel products;
    private final SellerOrdersPanel orders;

    public SellerWorkspacePanel(SellerShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout());
        ShopComponentStyle.pagePanel(this);
        products = new ProductManagementPanel(port, uiKit, sessionExpired);
        orders = new SellerOrdersPanel(port, uiKit, sessionExpired);
        profile = new ShopProfilePanel(port, uiKit, sessionExpired, products::setShop);
        JTabbedPane tabs = new JTabbedPane(); tabs.setName("seller.workspace.tabs");
        ShopComponentStyle.styleTabbedPane(tabs);
        tabs.addTab("店铺资料", profile); tabs.addTab("商品管理", products); tabs.addTab("订单", orders);
        add(tabs, BorderLayout.CENTER);
    }
    public void load() { profile.load(); products.load(); orders.load(); }
    public void disposePage() { profile.disposePage(); products.disposePage(); orders.disposePage(); }
}
