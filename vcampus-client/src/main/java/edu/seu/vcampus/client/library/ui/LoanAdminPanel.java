package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.Objects;
import java.awt.*;
public final class LoanAdminPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    public LoanAdminPanel(LibraryClientService service) {
        super("library.loan-admin", "借阅管理", "查询全校借阅及逾期记录。", "借阅号", "借阅人", "副本", "到期时间", "状态");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("查询借阅"); refresh.addActionListener(event -> refresh());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(refresh); add(actions, BorderLayout.SOUTH);
    }
    public void refresh() {
        long request = beginRequest();
        status.setText("正在加载全校借阅……");
        service.searchAllLoans(new AdminLoanSearchQuery(null, null, 1, 20)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) { status.setText("借阅记录加载失败，请重试"); return; }
                    DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
                    for (LoanView loan : page.items()) model.addRow(new Object[]{loan.loanId(), loan.borrowerUserId(), loan.copyId(), loan.dueAt(), loan.status().name()});
                    status.setText(page.items().isEmpty() ? "未找到借阅记录" : "共 " + page.total() + " 条借阅记录");
                }));
    }
}
