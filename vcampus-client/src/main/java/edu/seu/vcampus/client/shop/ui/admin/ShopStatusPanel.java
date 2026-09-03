package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.client.shop.ui.style.ShopComponentStyle;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Administrative shop search and suspension controls. */
public final class ShopStatusPanel extends JPanel {
    private final AdminShopClientPort port;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"店铺 ID", "店主", "店铺名称", "类别", "状态", "商品数"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = named(new JTable(model), "admin.shops.table");
    private final JLabel status = named(new JLabel(), "admin.shops.status");
    private final List<ShopAdminSummary> rows = new ArrayList<>();
    private boolean disposed;

    public ShopStatusPanel(AdminShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        ShopComponentStyle.pagePanel(this);
        ShopComponentStyle.styleTable(table, true);
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        Objects.requireNonNull(uiKit, "uiKit");
        JButton suspend = uiKit.secondaryButton("admin.shops.suspend", "停业");
        JButton resume = uiKit.primaryButton("admin.shops.resume", "恢复营业");
        suspend.addActionListener(event -> {
            String reason = JOptionPane.showInputDialog(this, "请输入停业原因");
            if (reason != null && !reason.isBlank()) suspend(reason.strip());
        });
        resume.addActionListener(event -> resume());
        JPanel actions = uiKit.filterPanel("admin.shops.actions", new java.awt.FlowLayout());
        actions.add(suspend); actions.add(resume); actions.add(status);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.searchShops(new ShopAdminQuery(null, null, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    rows.clear(); rows.addAll(page.items()); model.setRowCount(0);
                    for (ShopAdminSummary value : rows) model.addRow(new Object[]{
                            value.shopId(), value.ownerUserId(), value.shopName(), value.category(),
                            value.status().name(), value.productCount()});
                }));
    }

    public void disposePage() { disposed = true; requests.dispose(); }

    private void suspend(String reason) {
        ShopAdminSummary value = selected();
        if (value == null) return;
        port.suspendShop(new SuspendShopCommand(value.shopId(), reason, value.rowVersion()))
                .whenComplete((ignored, failure) -> finishMutation(failure));
    }

    private void resume() {
        ShopAdminSummary value = selected();
        if (value == null) return;
        port.resumeShop(new ResumeShopCommand(value.shopId(), value.rowVersion()))
                .whenComplete((ignored, failure) -> finishMutation(failure));
    }

    private ShopAdminSummary selected() {
        int selected = table.getSelectedRow();
        return selected < 0 || selected >= rows.size() ? null : rows.get(selected);
    }

    private void finishMutation(Throwable failure) {
        SwingUtilities.invokeLater(() -> { if (failure != null) fail(failure); else load(); });
    }

    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        status.setText(ShopUiErrors.message(code));
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
