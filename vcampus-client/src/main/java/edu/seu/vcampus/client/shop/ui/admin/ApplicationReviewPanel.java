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
    @FunctionalInterface
    interface DetailDialog {
        Optional<DetailReview> show(Component parent, SellerApplicationView application,
                boolean reviewable);
    }

    record DetailReview(SellerReviewDecision decision, String reason) { }

    private final AdminShopClientPort port; private final Runnable sessionExpired;
    private final DetailDialog dialogs;
    private final LatestRequest pendingRequests = new LatestRequest(), processedRequests = new LatestRequest();
    private final DefaultTableModel pendingModel = model(), processedModel = model();
    private final JTable pending = named(new JTable(pendingModel), "admin.applications.pending");
    private final JTable processed = named(new JTable(processedModel), "admin.applications.processed");
    private final JLabel detail = named(new JLabel(), "admin.applications.detail");
    private final List<SellerApplicationView> pendingRows = new ArrayList<>();
    private final List<SellerApplicationView> processedRows = new ArrayList<>();
    private final JButton approve;
    private final JButton reject;
    private final JButton refresh;
    private long refreshGeneration;
    private int refreshOutstanding;
    private boolean disposed;
    public ApplicationReviewPanel(AdminShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired) {
        this(port, uiKit, sessionExpired, ApplicationDetailDialog::show);
    }
    ApplicationReviewPanel(AdminShopClientPort port, ShopUiKit uiKit, Runnable sessionExpired,
            DetailDialog dialogs) {
        super(new BorderLayout(8, 8)); this.port = Objects.requireNonNull(port); this.sessionExpired = Objects.requireNonNull(sessionExpired);
        this.dialogs = Objects.requireNonNull(dialogs);
        pending.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                if (pending.getSelectedRow() >= 0) processed.clearSelection();
                showSelection();
            }
        });
        processed.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                if (processed.getSelectedRow() >= 0) pending.clearSelection();
                showSelection();
            }
        });
        pending.addMouseListener(doubleClick(() -> selected(pending, pendingRows), true));
        processed.addMouseListener(doubleClick(() -> selected(processed, processedRows), false));
        JTabbedPane tabs = named(new JTabbedPane(), "admin.applications.tabs");
        tabs.addTab("未处理", new JScrollPane(pending)); tabs.addTab("已处理", new JScrollPane(processed));
        approve = uiKit.primaryButton("admin.applications.approve", "通过");
        reject = uiKit.secondaryButton("admin.applications.reject", "驳回");
        refresh = uiKit.secondaryButton("admin.applications.refresh", "刷新");
        approve.setEnabled(false); reject.setEnabled(false);
        refresh.addActionListener(event -> load());
        approve.addActionListener(event -> review(SellerReviewDecision.APPROVE, null));
        reject.addActionListener(event -> { String reason = JOptionPane.showInputDialog(this, "请输入驳回原因");
            if (reason != null && !reason.isBlank()) review(SellerReviewDecision.REJECT, reason.strip()); });
        JPanel actions = uiKit.filterPanel("admin.applications.actions", new FlowLayout());
        actions.add(refresh); actions.add(approve); actions.add(reject);
        JPanel south = uiKit.filterPanel("admin.applications.south", new BorderLayout()); south.add(detail); south.add(actions, BorderLayout.SOUTH);
        add(tabs); add(south, BorderLayout.SOUTH);
    }
    public void load() {
        if (disposed) return;
        long generation = ++refreshGeneration; refreshOutstanding = 2; refresh.setEnabled(false);
        load(SellerApplicationListMode.PENDING, generation);
        load(SellerApplicationListMode.PROCESSED, generation);
    }
    private void load(SellerApplicationListMode mode, long generation) {
        if (disposed) return; LatestRequest requests = mode == SellerApplicationListMode.PENDING ? pendingRequests : processedRequests;
        long request = requests.begin(); port.searchApplications(new SellerApplicationQuery(null, mode, 0, 50))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!requests.accepts(request)) return;
                    if (failure != null) { fail(failure); finishRefresh(generation); return; }
                    DefaultTableModel target = mode == SellerApplicationListMode.PENDING ? pendingModel : processedModel;
                    if (mode == SellerApplicationListMode.PENDING) { pendingRows.clear(); pendingRows.addAll(page.items()); }
                    else { processedRows.clear(); processedRows.addAll(page.items()); }
                    target.setRowCount(0); for (SellerApplicationView value : page.items()) target.addRow(new Object[]{
                            value.applicationId(), value.applicantUserId(), value.shopName(), value.category(), value.status().name()});
                    finishRefresh(generation);
                }));
    }
    private void finishRefresh(long generation) {
        if (generation != refreshGeneration || refreshOutstanding == 0) return;
        if (--refreshOutstanding == 0) refresh.setEnabled(true);
    }
    public void disposePage() { disposed = true; pendingRequests.dispose(); processedRequests.dispose(); }
    private void showSelection() { int row = pending.getSelectedRow(); boolean reviewable = row >= 0 && row < pendingRows.size();
        approve.setEnabled(reviewable); reject.setEnabled(reviewable);
        if (reviewable) { detail.setText("已选择待处理申请"); return; }
        int processedRow = processed.getSelectedRow();
        detail.setText(processedRow >= 0 && processedRow < processedRows.size() ? "已选择审核记录" : ""); }
    private void review(SellerReviewDecision decision, String reason) { int row = pending.getSelectedRow(); if (row < 0 || row >= pendingRows.size()) return;
        review(pendingRows.get(row), decision, reason); }
    private void review(SellerApplicationView value, SellerReviewDecision decision, String reason) {
        port.reviewApplication(new ReviewSellerApplicationCommand(value.applicationId(), decision, reason, value.rowVersion()))
                .whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> { if (disposed) return; if (failure != null) fail(failure); else load(); })); }
    private java.awt.event.MouseAdapter doubleClick(java.util.function.Supplier<SellerApplicationView> selected,
            boolean reviewable) { return new java.awt.event.MouseAdapter() {
        @Override public void mouseClicked(java.awt.event.MouseEvent event) {
            if (event.getClickCount() != 2) return;
            SellerApplicationView value = selected.get(); if (value == null) return;
            dialogs.show(ApplicationReviewPanel.this, value, reviewable)
                    .ifPresent(result -> review(value, result.decision(), result.reason()));
        }}; }
    private static SellerApplicationView selected(JTable table, List<SellerApplicationView> rows) {
        int row = table.getSelectedRow(); return row >= 0 && row < rows.size() ? rows.get(row) : null;
    }
    private void fail(Throwable failure) { String code = ShopUiErrors.code(failure);
        detail.setText(ShopUiErrors.message(code));
        if (ShopUiErrors.sessionExpired(code)) sessionExpired.run(); }
    private static DefaultTableModel model() { return new DefaultTableModel(new Object[]{"申请 ID", "申请人", "店铺名称", "类别", "状态"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }}; }
    private static <T extends JComponent> T named(T value, String name) { value.setName(name); return value; }
}
