package edu.seu.vcampus.client.shop.ui.admin;
import edu.seu.vcampus.client.shop.service.AdminShopClientPort;
import edu.seu.vcampus.client.shop.ui.*;
import edu.seu.vcampus.client.shop.ui.async.LatestRequest;
import edu.seu.vcampus.client.shop.ui.style.ShopUiKit;
import edu.seu.vcampus.common.shop.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Latest-first pending and processed seller-application queues. */
public final class ApplicationReviewPanel extends JPanel {
    private final AdminShopClientPort port; private final Runnable sessionExpired;
    private final LatestRequest pendingRequests = new LatestRequest(), processedRequests = new LatestRequest();
    private final DefaultTableModel pendingModel = model(), processedModel = model();
    private final JTable pending = named(new JTable(pendingModel), "admin.applications.pending");
    private final JTable processed = named(new JTable(processedModel), "admin.applications.processed");
    private final JLabel detail = named(new JLabel(), "admin.applications.detail");
    private final List<SellerApplicationView> pendingRows = new ArrayList<>();
    private boolean disposed;
    public ApplicationReviewPanel(AdminShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        super(new BorderLayout(8, 8)); this.port = Objects.requireNonNull(port); this.sessionExpired = Objects.requireNonNull(sessionExpired);
        pending.getSelectionModel().addListSelectionListener(event -> showSelection());
        JTabbedPane tabs = named(new JTabbedPane(), "admin.applications.tabs");
        tabs.addTab("未处理", new JScrollPane(pending)); tabs.addTab("已处理", new JScrollPane(processed));
        JButton approve = uiKit.primaryButton("admin.applications.approve", "通过");
        JButton reject = uiKit.secondaryButton("admin.applications.reject", "驳回");
        approve.addActionListener(event -> review(SellerReviewDecision.APPROVE, null));
        reject.addActionListener(event -> { String reason = JOptionPane.showInputDialog(this, "请输入驳回原因");
            if (reason != null && !reason.isBlank()) review(SellerReviewDecision.REJECT, reason.strip()); });
        JPanel actions = uiKit.filterPanel("admin.applications.actions", new FlowLayout()); actions.add(approve); actions.add(reject);
        JPanel south = uiKit.filterPanel("admin.applications.south", new BorderLayout()); south.add(detail); south.add(actions, BorderLayout.SOUTH);
        add(tabs); add(south, BorderLayout.SOUTH);
    }
    public void load() { load(SellerApplicationListMode.PENDING); load(SellerApplicationListMode.PROCESSED); }
    private void load(SellerApplicationListMode mode) {
        if (disposed) return; LatestRequest requests = mode == SellerApplicationListMode.PENDING ? pendingRequests : processedRequests;
        long request = requests.begin(); port.searchApplications(new SellerApplicationQuery(null, mode, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return; if (failure != null) { fail(failure); return; }
                    DefaultTableModel target = mode == SellerApplicationListMode.PENDING ? pendingModel : processedModel;
                    if (mode == SellerApplicationListMode.PENDING) { pendingRows.clear(); pendingRows.addAll(page.items()); }
                    target.setRowCount(0); for (SellerApplicationView value : page.items()) target.addRow(new Object[]{
                            value.applicationId(), value.applicantUserId(), value.shopName(), value.category(), value.status().name()});
                }));
    }
    public void disposePage() { disposed = true; pendingRequests.dispose(); processedRequests.dispose(); }
    private void showSelection() { int row = pending.getSelectedRow(); if (row < 0 || row >= pendingRows.size()) { detail.setText(""); return; }
        SellerApplicationView value = pendingRows.get(row); detail.setText("联系方式: " + value.contact() + " | 经营计划: " + value.applicationStatement()); }
    private void review(SellerReviewDecision decision, String reason) { int row = pending.getSelectedRow(); if (row < 0 || row >= pendingRows.size()) return;
        SellerApplicationView value = pendingRows.get(row); port.reviewApplication(new ReviewSellerApplicationCommand(value.applicationId(), decision, reason, value.rowVersion()))
                .whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> { if (disposed) return; if (failure != null) fail(failure); else load(); })); }
    private void fail(Throwable failure) { String code = ShopUiErrors.code(failure); detail.setText(code); if (ShopUiErrors.sessionExpired(code)) sessionExpired.run(); }
    private static DefaultTableModel model() { return new DefaultTableModel(new Object[]{"申请 ID", "申请人", "店铺名称", "类别", "状态"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }}; }
    private static <T extends JComponent> T named(T value, String name) { value.setName(name); return value; }
}
