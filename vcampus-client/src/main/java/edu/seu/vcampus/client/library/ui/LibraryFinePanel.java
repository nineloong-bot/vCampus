package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.LibraryFineQuery;
import edu.seu.vcampus.common.library.LibraryFineView;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Paginated fine receipts for administrators and confirmed wallet payments for borrowers. */
public final class LibraryFinePanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final boolean administrator;
    private final JButton pay = new JButton("缴纳所选罚款");
    private final JButton refresh = new JButton("刷新罚款");
    private final LibraryPagination pagination = new LibraryPagination(this::loadPage);
    private List<LibraryFineView> fines = List.of();
    private boolean busy;

    /** Creates an own-fines payment page or an administrator's read-only receipt page. */
    public LibraryFinePanel(LibraryClientService service, boolean administrator) {
        super("library.fines", administrator ? "罚款记录" : "罚款缴纳",
                administrator ? "查看归还、损坏及遗失罚款的缴纳状态。" : "归还或认定遗失后，可使用商店共用钱包缴纳罚款。",
                "借阅号", "书名", "借阅人", "逾期罚金（元）", "赔偿（元）", "合计（元）", "缴纳状态", "缴费收据");
        this.service = Objects.requireNonNull(service);
        this.administrator = administrator;
        setColumnWidths(130, 220, 120, 120, 100, 110, 100, 280);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(refresh);
        if (!administrator) actions.add(pay);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(pagination, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refresh());
        pay.addActionListener(event -> confirmPayment());
        table.getSelectionModel().addListSelectionListener(event -> updatePayButton());
        updatePayButton();
    }

    /** Refreshes assessed amounts and authoritative wallet receipts from the first page. */
    public void refresh() {
        loadPage(1);
    }

    /** Clears pending UI state when detached; lifecycle guards discard the old asynchronous result. */
    @Override public void removeNotify() {
        setBusy(false);
        super.removeNotify();
    }

    private void loadPage(int pageNumber) {
        if (busy) return;
        long request = beginRequest();
        setBusy(true);
        status.setText("正在加载罚款……");
        var query = new LibraryFineQuery(pageNumber, 20);
        var response = administrator ? service.getAllFines(query) : service.getMyFines(query);
        response.whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            setBusy(false);
            if (failure != null) {
                LibraryFeedback.failure(this, status, failure, "罚款加载失败，请重试。");
                return;
            }
            if (page.items().isEmpty() && pageNumber > 1) {
                loadPage((int) Math.max(1, (page.total() + 19) / 20));
                return;
            }
            fines = List.copyOf(page.items());
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.setRowCount(0);
            for (var fine : fines) {
                var loan = fine.loan();
                model.addRow(new Object[]{loan.displayLoanNumber(), LoanUiText.title(loan),
                        loan.borrowerLoginId() == null ? loan.borrowerUserId() : loan.borrowerLoginId(),
                        loan.overdueFine().setScale(2), loan.damageFine().setScale(2), loan.totalFine().setScale(2),
                        fine.paid() ? "已缴纳" : "待缴纳", fine.paymentId() == null ? "—" : fine.paymentId()});
            }
            pagination.showPage(page.page(), page.pageSize(), page.total());
            updatePayButton();
            status.setText(page.total() == 0 ? "暂无罚款" : "共 " + page.total() + " 条罚款记录");
        }));
    }

    private LibraryFineView selected() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int index = table.convertRowIndexToModel(row);
        return index < fines.size() ? fines.get(index) : null;
    }

    private void updatePayButton() {
        var fine = selected();
        pay.setEnabled(!administrator && !busy && fine != null && !fine.paid());
    }

    private void setBusy(boolean value) {
        busy = value;
        table.setEnabled(!value);
        refresh.setEnabled(!value);
        pagination.setLoading(value);
        updatePayButton();
    }

    private void confirmPayment() {
        var fine = selected();
        if (administrator || busy || fine == null || fine.paid()) return;
        String message = "确认从共用钱包缴纳《" + LoanUiText.title(fine.loan()) + "》的罚款 "
                + fine.loan().totalFine().setScale(2) + " 元？";
        if (JOptionPane.showConfirmDialog(this, message, "确认缴纳罚款",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (busy) return;
        long request = beginMutation();
        setBusy(true);
        status.setText("正在缴纳罚款……");
        service.payFine(fine.loan().loanId()).whenComplete((receipt, failure) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsMutation(request)) return;
            setBusy(false);
            if (failure != null) {
                LibraryFeedback.failure(this, status, failure, "缴费结果未确认，请刷新后重试；同一罚款不会重复扣款。");
                return;
            }
            var updated = new ArrayList<>(fines);
            for (int index = 0; index < updated.size(); index++) {
                if (!updated.get(index).loan().loanId().equals(fine.loan().loanId())) continue;
                updated.set(index, new LibraryFineView(fine.loan(), receipt.operationId()));
                table.getModel().setValueAt("已缴纳", index, 6);
                table.getModel().setValueAt(receipt.operationId(), index, 7);
            }
            fines = List.copyOf(updated);
            updatePayButton();
            status.setText("缴纳成功，支付后钱包余额 " + BigDecimal.valueOf(receipt.balanceCents(), 2) + " 元");
        }));
    }
}
