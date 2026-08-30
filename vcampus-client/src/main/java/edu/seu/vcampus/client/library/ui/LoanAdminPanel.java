package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.Objects;
import java.awt.*;
public final class LoanAdminPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField borrower = new JTextField(12);
    private final JComboBox<String> loanStatus = new JComboBox<>(new String[]{"全部状态", "ACTIVE", "OVERDUE", "RETURNED", "LOST"});
    public LoanAdminPanel(LibraryClientService service) {
        super("library.loan-admin", "借阅管理", "查询全校借阅及逾期记录。", "借阅号", "借阅人", "副本", "到期时间", "状态");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("筛选借阅"); refresh.addActionListener(event -> refresh());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(new JLabel("借阅人账号或编号")); actions.add(borrower); actions.add(loanStatus);
        actions.add(refresh); add(actions, BorderLayout.SOUTH);
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
                    DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
                    for (LoanView loan : page.items()) model.addRow(new Object[]{
                            loan.displayLoanNumber(), readable(loan.borrowerLoginId(), loan.borrowerUserId()),
                            copyDescription(loan), loan.dueAt(), loan.status().name()});
                    status.setText(page.items().isEmpty() ? "未找到借阅记录" : "共 " + page.total() + " 条借阅记录");
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
