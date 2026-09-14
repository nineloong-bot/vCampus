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
    private final JComboBox<String> condition = new JComboBox<>(new String[]{"完好", "轻度损坏", "严重损坏"});
    private final LibraryPagination pagination = new LibraryPagination(this::loadPage);
    private AdminLoanSearchQuery loadedQuery;
    private List<LoanView> loans = List.of();
    public LoanAdminPanel(LibraryClientService service) {
        super("library.loan-admin", "借阅管理", "查询全校借阅；归还或遗失登记时计算罚金，仅登记金额。", "借阅号", "借阅人", "副本", "到期时间", "状态", "归还情况", "逾期罚金（元）", "赔偿（元）", "罚金合计（元）");
        this.service = Objects.requireNonNull(service, "service");
        setColumnWidths(130, 110, 260, 210, 90, 100, 130, 110, 140);
        JButton refresh = new JButton("查询账号"); refresh.addActionListener(event -> refresh());
        JButton returnBook = new JButton("办理归还"); returnBook.addActionListener(event -> confirmSelected(LoanStatus.RETURNED));
        JButton markLost = new JButton("标记遗失"); markLost.addActionListener(event -> confirmSelected(LoanStatus.LOST));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT)); filters.setOpaque(false);
        filters.add(new JLabel("账号（精确查询）")); filters.add(borrower); filters.add(loanStatus); filters.add(refresh);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(new JLabel("归还情况")); actions.add(condition); actions.add(returnBook); actions.add(markLost);
        JPanel footer = new JPanel(new GridLayout(0, 1)); footer.setOpaque(false);
        footer.add(filters); footer.add(actions); footer.add(pagination); add(footer, BorderLayout.SOUTH);
        borrower.addActionListener(event -> refresh());
    }
    public void refresh() { loadPage(1); }

    private void loadPage(int requestedPage) {
        long request = beginRequest();
        status.setText("正在加载全校借阅……");
        String selected = (String) loanStatus.getSelectedItem();
        LoanStatus filter = "全部状态".equals(selected) ? null : LoanStatus.valueOf(selected);
        String user = borrower.getText().trim();
        String userId = user.isEmpty() ? null : user;
        int target = loadedQuery == null || !Objects.equals(userId, loadedQuery.borrowerUserId())
                || filter != loadedQuery.status() ? 1 : requestedPage;
        AdminLoanSearchQuery query = new AdminLoanSearchQuery(userId, filter, target, 20);
        pagination.setLoading(true); table.setEnabled(false);
        service.searchAllLoans(query).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    pagination.setLoading(false); table.setEnabled(true);
                    if (failure != null) { LibraryFeedback.failure(this, status, failure, "借阅记录加载失败，请重试。"); return; }
                    if (page.items().isEmpty() && target > 1) {
                        loadPage((int) Math.max(1, (page.total() + 19) / 20)); return;
                    }
                    loadedQuery = query;
                    pagination.showPage(page.page(), page.pageSize(), page.total());
                    loans = List.copyOf(page.items());
                    DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
                    for (LoanView loan : loans) model.addRow(new Object[]{
                            loan.displayLoanNumber(), readable(loan.borrowerLoginId(), loan.borrowerUserId()),
                            copyDescription(loan), loan.dueAt(), LoanUiText.status(loan.status()),
                            LoanUiText.condition(loan), loan.overdueFine().setScale(2), loan.damageFine().setScale(2),
                            loan.totalFine().setScale(2)});
                    status.setText(page.items().isEmpty() ? "未找到借阅记录" : (user.isEmpty()
                            ? "共 " + page.total() + " 条借阅记录"
                            : "正在管理账号 " + user.toUpperCase(java.util.Locale.ROOT) + "，共 " + page.total() + " 条记录"));
                }));
    }

    public void returnSelected() { resolveSelected(LoanStatus.RETURNED); }

    public void markLostSelected() { resolveSelected(LoanStatus.LOST); }

    private void resolveSelected(LoanStatus resolution) {
        LoanView loan = selectedActiveLoan();
        if (loan != null) resolve(loan, resolution);
    }

    private void confirmSelected(LoanStatus resolution) {
        LoanView loan = selectedActiveLoan();
        if (loan == null) return;
        String action = resolution == LoanStatus.RETURNED ? "办理归还" : "确认遗失登记";
        String subject = readable(loan.borrowerLoginId(), loan.borrowerUserId()) + " · "
                + readable(loan.bookTitle(), loan.bookId()) + " · "
                + readable(loan.copyBarcode(), loan.copyId());
        new LoanActionDialog(SwingUtilities.getWindowAncestor(this), action, subject,
                () -> resolve(loan, resolution)).setVisible(true);
    }

    private LoanView selectedActiveLoan() {
        if (!table.isEnabled()) { status.setText("请等待借阅记录加载完成"); return null; }
        int selected = table.getSelectedRow();
        if (selected < 0 || selected >= loans.size()) { status.setText("请先选择一条有效借阅记录"); return null; }
        LoanView loan = loans.get(table.convertRowIndexToModel(selected));
        if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.OVERDUE) {
            status.setText("所选借阅已经结束，不能重复处理"); return null;
        }
        return loan;
    }

    private void resolve(LoanView loan, LoanStatus resolution) {
        long request = beginMutation();
        status.setText(resolution == LoanStatus.RETURNED ? "正在办理归还……" : "正在标记遗失……");
        service.resolveLoan(new AdminResolveLoanCommand(loan.loanId(), resolution, loan.rowVersion(),
                resolution == LoanStatus.LOST ? ReturnCondition.LOST : ReturnCondition.values()[condition.getSelectedIndex()]))
                .whenComplete((resolved, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!acceptsMutation(request)) return;
                    if (failure != null) {
                        LibraryFeedback.failure(this, status, failure, "借阅处理失败，请刷新后重试。");
                        return;
                    }
                    status.setText(resolution == LoanStatus.RETURNED ? "归还已办理，用户借阅已同步" : "遗失已登记，用户借阅已同步");
                    refresh();
                    mutationSucceeded();
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
