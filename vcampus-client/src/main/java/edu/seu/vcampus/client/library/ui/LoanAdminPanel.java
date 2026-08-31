package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.Objects;
import java.awt.*;
import java.util.List;
public final class LoanAdminPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField borrower = new JTextField(12);
    private final JComboBox<String> loanStatus = new JComboBox<>(new String[]{"全部状态", "ACTIVE", "OVERDUE", "RETURNED", "LOST"});
    private List<LoanView> loans = List.of();
    public LoanAdminPanel(LibraryClientService service) {
        super("library.loan-admin", "借阅管理", "查询全校借阅及逾期记录。", "借阅号", "借阅人", "副本", "到期时间", "状态");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("查询账号"); refresh.addActionListener(event -> refresh());
        JButton returnBook = new JButton("办理归还"); returnBook.addActionListener(event -> returnSelected());
        JButton markLost = new JButton("标记遗失"); markLost.addActionListener(event -> markLostSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(new JLabel("账号（精确查询）")); actions.add(borrower); actions.add(loanStatus);
        actions.add(refresh); actions.add(returnBook); actions.add(markLost); add(actions, BorderLayout.SOUTH);
        borrower.addActionListener(event -> refresh());
    }
    public void refresh() {
        long request = beginRequest();
        status.setText("正在加载全校借阅……");
        String selected = (String) loanStatus.getSelectedItem();
        LoanStatus filter = "全部状态".equals(selected) ? null : LoanStatus.valueOf(selected);
        String user = borrower.getText().trim();
        service.searchAllLoans(new AdminLoanSearchQuery(user.isEmpty() ? null : user, filter, 1, 20)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) { LibraryFeedback.failure(this, status, failure, "借阅记录加载失败，请重试。"); return; }
                    loans = List.copyOf(page.items());
                    DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
                    for (LoanView loan : loans) model.addRow(new Object[]{
                            loan.displayLoanNumber(), readable(loan.borrowerLoginId(), loan.borrowerUserId()),
                            copyDescription(loan), loan.dueAt(), LoanUiText.status(loan.status())});
                    status.setText(page.items().isEmpty() ? "未找到借阅记录" : (user.isEmpty()
                            ? "共 " + page.total() + " 条借阅记录"
                            : "正在管理账号 " + user.toUpperCase(java.util.Locale.ROOT) + "，共 " + page.total() + " 条记录"));
                }));
    }

    public void returnSelected() { resolveSelected(LoanStatus.RETURNED); }

    public void markLostSelected() { resolveSelected(LoanStatus.LOST); }

    private void resolveSelected(LoanStatus resolution) {
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= loans.size()) { status.setText("请先选择一条有效借阅记录"); return; }
        LoanView loan = loans.get(table.convertRowIndexToModel(selected));
        if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.OVERDUE) {
            status.setText("所选借阅已经结束，不能重复处理"); return;
        }
        long request = beginRequest();
        status.setText(resolution == LoanStatus.RETURNED ? "正在办理归还……" : "正在标记遗失……");
        service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), resolution, loan.rowVersion()))
                .whenComplete((resolved, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) {
                        LibraryFeedback.failure(this, status, failure, "借阅处理失败，请刷新后重试。");
                        return;
                    }
                    status.setText(resolution == LoanStatus.RETURNED ? "归还已办理，用户借阅已同步" : "遗失已登记，用户借阅已同步");
                    refresh();
                }));
    }

    private static String copyDescription(LoanView loan) {
        String title = readable(loan.bookTitle(), loan.bookId());
        String barcode = readable(loan.copyBarcode(), loan.copyId());
        return title + " / " + barcode;
    }

    private static String readable(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
