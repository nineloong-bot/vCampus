package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Detail template for a selected title and its borrowable physical copies. */
public final class BookDetailPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private List<BookCopyView> copies = List.of();

    public BookDetailPanel(LibraryClientService service) {
        this(service, true);
    }

    BookDetailPanel(LibraryClientService service, boolean borrowingEnabled) {
        super("library.book-detail", "图书详情", "查看书目信息和馆藏副本。", "条码", "位置", "状态");
        this.service = Objects.requireNonNull(service, "service");
        if (!borrowingEnabled) return;
        JButton borrow = new JButton("借阅所选副本");
        borrow.setName("library.loan-action");
        borrow.addActionListener(event -> confirmBorrow());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false); actions.add(borrow); add(actions, BorderLayout.SOUTH);
    }

    public void showBook(BookDetail book) {
        copies = book.copies();
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (BookCopyView copy : copies) model.addRow(new Object[]{copy.barcode(), copy.locationCode(),
                LibraryStatusText.copy(copy.status())});
        status.setText(book.title() + " · " + book.author() + " · " + book.isbn());
    }

    void clearBook() {
        copies = List.of();
        ((DefaultTableModel) table.getModel()).setRowCount(0);
        status.setText("请选择一本书目查看详情");
    }

    public void borrowSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= copies.size()) {
            LibraryFeedback.borrowWarning(this, status, "请先选择一个可借副本"); return;
        }
        BookCopyView copy = copies.get(table.convertRowIndexToModel(row));
        if (copy.status() != CopyStatus.AVAILABLE) {
            LibraryFeedback.borrowWarning(this, status, "该副本当前不可借，请选择可借副本"); return;
        }
        status.setText("正在办理借阅……");
        long request = beginMutation();
        service.borrow(new BorrowBookCommand(copy.copyId())).whenComplete((loan, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!acceptsMutation(request)) return;
                    if (failure == null) { status.setText("借阅成功，到期时间：" + loan.dueAt()); mutationSucceeded(); }
                    else LibraryFeedback.borrowFailure(this, status, failure,
                            "借阅失败，请刷新馆藏后重试。");
                }));
    }

    private void confirmBorrow() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= copies.size()) {
            LibraryFeedback.borrowWarning(this, status, "请先选择一个可借副本"); return;
        }
        BookCopyView copy = copies.get(table.convertRowIndexToModel(row));
        Window owner = SwingUtilities.getWindowAncestor(this);
        new LoanActionDialog(owner, "借阅", "馆藏条码 " + copy.barcode(), this::borrowSelected).setVisible(true);
    }
}
