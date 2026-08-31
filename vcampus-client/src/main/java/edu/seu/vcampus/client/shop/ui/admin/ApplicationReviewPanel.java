package edu.seu.vcampus.client.shop.ui.admin;

import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.ShopUiErrors;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Administrative seller-application list and review actions. */
public final class ApplicationReviewPanel extends JPanel {
    private final AdminShopClientPort port;
    private final Runnable sessionExpired;
    private final LatestRequest requests = new LatestRequest();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"申请 ID", "申请人", "店铺名称", "类别", "状态"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = named(new JTable(model), "admin.applications.table");
    private final JLabel detail = named(new JLabel(), "admin.applications.detail");
    private final List<SellerApplicationView> rows = new ArrayList<>();
    private boolean disposed;

    public ApplicationReviewPanel(AdminShopClientPort port, ShopUiKit uiKit,
            Runnable sessionExpired) {
        super(new BorderLayout(8, 8));
        this.port = Objects.requireNonNull(port, "port");
        this.sessionExpired = Objects.requireNonNull(sessionExpired, "sessionExpired");
        Objects.requireNonNull(uiKit, "uiKit");
        table.getSelectionModel().addListSelectionListener(event -> showSelection());
        JButton approve = uiKit.primaryButton("admin.applications.approve", "通过");
        JButton reject = uiKit.secondaryButton("admin.applications.reject", "驳回");
        approve.addActionListener(event -> review(SellerReviewDecision.APPROVE, null));
        reject.addActionListener(event -> {
            String reason = JOptionPane.showInputDialog(this, "请输入驳回原因");
            if (reason != null && !reason.isBlank()) review(SellerReviewDecision.REJECT, reason.strip());
        });
        JPanel actions = uiKit.filterPanel("admin.applications.actions", new java.awt.FlowLayout());
        actions.add(approve); actions.add(reject);
        JPanel south = uiKit.filterPanel("admin.applications.south", new BorderLayout());
        south.add(detail, BorderLayout.CENTER); south.add(actions, BorderLayout.SOUTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    public void load() {
        if (disposed) return;
        long request = requests.begin();
        port.searchApplications(new SellerApplicationQuery(null, null, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); return; }
                    rows.clear(); rows.addAll(page.items()); model.setRowCount(0);
                    for (SellerApplicationView value : rows) model.addRow(new Object[]{
                            value.applicationId(), value.applicantUserId(), value.shopName(),
                            value.category(), value.status().name()});
                }));
    }

    public void disposePage() { disposed = true; requests.dispose(); }

    private void showSelection() {
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= rows.size()) { detail.setText(""); return; }
        SellerApplicationView value = rows.get(selected);
        detail.setText("联系方式: " + value.contact() + " | 经营计划: " + value.applicationStatement());
    }

    private void review(SellerReviewDecision decision, String reason) {
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= rows.size()) return;
        SellerApplicationView value = rows.get(selected);
        port.reviewApplication(new ReviewSellerApplicationCommand(value.applicationId(), decision,
                reason, value.rowVersion())).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
                    if (disposed) return;
                    if (failure != null) fail(failure); else load();
                }));
    }

    private void fail(Throwable failure) {
        String code = ShopUiErrors.code(failure);
        detail.setText(code);
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run();
    }

    private static <T extends JComponent> T named(T component, String name) {
        component.setName(name); return component;
    }
}
