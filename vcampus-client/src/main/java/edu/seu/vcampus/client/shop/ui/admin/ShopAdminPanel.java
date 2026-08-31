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

    public ShopAdminPanel(AdminShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout());
        applications = new ApplicationReviewPanel(port, uiKit, sessionExpired);
        shops = new ShopStatusPanel(port, uiKit, sessionExpired);
        JTabbedPane tabs = new JTabbedPane();
        tabs.setName("admin.workspace.tabs");
        tabs.addTab("开店审核", applications);
        tabs.addTab("店铺治理", shops);
        add(tabs, BorderLayout.CENTER);
    }

    public void load() { applications.load(); shops.load(); }
    public void disposePage() { applications.disposePage(); shops.disposePage(); }
}
