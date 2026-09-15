package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.Objects;
import java.awt.*;
public final class LoanHistoryPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final LibraryPagination pagination = new LibraryPagination(this::loadPage);
    public LoanHistoryPanel(LibraryClientService service) {
        super("library.loan-history", "借阅历史", "查询本人全部借阅记录。", "借阅号", "书名", "馆藏条码",
                "借出时间", "到期时间", "归还时间", "续借次数", "状态", "归还情况", "逾期罚金（元）", "赔偿（元）", "罚金合计（元）");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("刷新历史"); refresh.addActionListener(event -> refresh());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(refresh); actions.add(pagination); add(actions, BorderLayout.SOUTH);
    }
    public void refresh() { loadPage(1); }
    private void loadPage(int pageNumber) {
        long request = beginRequest();
        status.setText("正在加载借阅历史……");
        pagination.setLoading(true);
        service.getLoanHistory(new LoanHistoryQuery(null, pageNumber, 20)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    pagination.setLoading(false);
                    if (failure != null) { LibraryFeedback.failure(this, status, failure, "借阅历史加载失败，请重试。"); return; }
                    pagination.showPage(page.page(), page.pageSize(), page.total());
                    DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
                    for (LoanView loan : page.items()) model.addRow(new Object[]{loan.displayLoanNumber(),
                            LoanUiText.title(loan), LoanUiText.barcode(loan), loan.borrowedAt(), loan.dueAt(),
                            loan.returnedAt(), loan.renewCount(), LoanUiText.status(loan.status()), LoanUiText.condition(loan),
                            loan.overdueFine().setScale(2), loan.damageFine().setScale(2), loan.totalFine().setScale(2)});
                    status.setText(page.items().isEmpty() ? "暂无借阅历史" : "共 " + page.total() + " 条借阅历史");
                }));
    }
}
