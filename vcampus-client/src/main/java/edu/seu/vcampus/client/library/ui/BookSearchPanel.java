package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Objects;
import java.util.List;

/** Searchable catalog page with latest-request protection. */
public final class BookSearchPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField keyword = new JTextField(24);
    private final JButton search = new JButton("查询馆藏");
    private List<BookSummary> books = List.of();
    private BookDetailPanel detail;

    public BookSearchPanel(LibraryClientService service) {
        super("library.book-search", "馆藏检索", "按书名、作者或 ISBN 检索可借馆藏。",
                "书名", "作者", "分类", "可借/总数");
        this.service = Objects.requireNonNull(service, "service");
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filters.setBackground(edu.seu.vcampus.client.core.ui.theme.UiColors.BACKGROUND_SUBTLE);
        filters.add(new JLabel("关键词"));
        filters.add(keyword);
        filters.add(search);
        add(filters, BorderLayout.SOUTH);
        search.addActionListener(event -> search());
        keyword.addActionListener(event -> search());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelectedDetail();
        });
    }

    public void connectDetail(BookDetailPanel detail) { this.detail = Objects.requireNonNull(detail); }

    public void search() {
        long request = beginRequest();
        search.setEnabled(false);
        status.setText("正在加载馆藏……");
        service.searchBooks(new BookSearchQuery(keyword.getText().trim(), null, false, 1, 20))
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    search.setEnabled(true);
                    if (failure != null) {
                        status.setText("馆藏加载失败，请重试");
                        return;
                    }
                    books = List.copyOf(page.items());
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                    for (BookSummary book : page.items()) model.addRow(new Object[]{book.title(),
                            book.author(), book.category(), book.availableCopies() + "/" + book.totalCopies()});
                    status.setText(page.items().isEmpty() ? "未找到符合条件的馆藏，可调整关键词重试"
                            : "共 " + page.total() + " 条");
                }));
    }

    private void loadSelectedDetail() {
        int row = table.getSelectedRow();
        if (detail == null || row < 0 || row >= books.size()) return;
        BookSummary book = books.get(table.convertRowIndexToModel(row));
        long request = beginRequest();
        status.setText("正在加载图书详情……");
        service.getBook(book.bookId()).whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure != null) status.setText("图书详情加载失败，请重试");
            else detail.showBook(result);
        }));
    }
}
