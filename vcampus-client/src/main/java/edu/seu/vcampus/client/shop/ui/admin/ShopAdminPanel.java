package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** Replaceable administrative workspace shell for applications and shop governance. */
public final class ShopAdminPanel extends JPanel {
    private final ApplicationReviewPanel applications;
    private final ShopStatusPanel shops;
    private final AdminProductManagementPanel products;

    public ShopAdminPanel(AdminShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout());
        applications = new ApplicationReviewPanel(port, uiKit, sessionExpired);
        shops = new ShopStatusPanel(port, uiKit, sessionExpired);
        products = new AdminProductManagementPanel(port, uiKit, sessionExpired);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("admin.workspace.tabs");
        tabs.addTab("开店审核", applications);
        tabs.addTab("店铺治理", shops);
        tabs.addTab("商品管理", products);
        add(tabs, BorderLayout.CENTER);
    }

    public void load() { applications.load(); shops.load(); products.load(); }
    public void disposePage() {
        applications.disposePage(); shops.disposePage(); products.disposePage();
    }
}
