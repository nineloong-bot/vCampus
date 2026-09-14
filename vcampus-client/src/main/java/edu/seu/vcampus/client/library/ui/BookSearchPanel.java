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
    private final JComboBox<String> field = new JComboBox<>(new String[]{
            "全部栏目", "书名", "作者", "ISBN", "分类", "出版社"});
    private final JButton search = new JButton("查询馆藏");
    private final LibraryPagination pagination = new LibraryPagination(this::loadPage);
    private BookSearchQuery loadedQuery;
    private long catalogRequest, detailSequence;
    private List<BookSummary> books = List.of();
    private BookDetailPanel detail;

    public BookSearchPanel(LibraryClientService service) {
        super("library.book-search", "馆藏检索", "按书名、作者或 ISBN 检索可借馆藏。",
                "书名", "作者", "分类", "可借/总数");
        this.service = Objects.requireNonNull(service, "service");
        setColumnWidths(180, 150, 100, 100);
        JPanel filters = new JPanel(new GridLayout(0, 1, 0, 8));
        filters.setBackground(LibraryPalette.SURFACE);
        filters.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LibraryPalette.BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JPanel keywordRow = new JPanel(new BorderLayout(8, 0)); keywordRow.setOpaque(false);
        keywordRow.add(new JLabel("关键词"), BorderLayout.WEST); keywordRow.add(keyword);
        JPanel searchRow = new JPanel(new BorderLayout(8, 0)); searchRow.setOpaque(false);
        field.setName("library.book-search-field");
        searchRow.add(field); searchRow.add(search, BorderLayout.EAST);
        filters.add(keywordRow); filters.add(searchRow);
        JPanel footer = new JPanel(new BorderLayout(0, 8)); footer.setOpaque(false);
        footer.add(filters); footer.add(pagination, BorderLayout.SOUTH); add(footer, BorderLayout.SOUTH);
        search.addActionListener(event -> search());
        keyword.addActionListener(event -> search());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelectedDetail();
        });
    }

    public void connectDetail(BookDetailPanel detail) { this.detail = Objects.requireNonNull(detail); }

    public void search() { loadPage(1); }

    private void loadPage(int requestedPage) {
        int selectedRow = table.getSelectedRow();
        String selectedBookId = selectedRow < 0 ? null : books.get(table.convertRowIndexToModel(selectedRow)).bookId();
        long request = beginRequest();
        catalogRequest = request;
        detailSequence++;
        search.setEnabled(false);
        pagination.setLoading(true); table.setEnabled(false);
        if (detail != null) detail.clearBook();
        status.setText("正在加载馆藏……");
        String text = keyword.getText().trim();
        BookSearchField searchField = selectedField();
        int target = loadedQuery == null || !text.equals(loadedQuery.keyword())
                || searchField != loadedQuery.field() ? 1 : requestedPage;
        BookSearchQuery query = new BookSearchQuery(text, searchField, null, false, target, 20);
        service.searchBooks(query)
                .whenComplete((page, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    search.setEnabled(true);
                    pagination.setLoading(false);
                    if (failure != null) {
                        table.setEnabled(true);
                        LibraryFeedback.failure(this, status, failure, "馆藏加载失败，请重试。");
                        return;
                    }
                    if (page.items().isEmpty() && target > 1) {
                        loadPage((int) Math.max(1, (page.total() + 19) / 20)); return;
                    }
                    loadedQuery = query;
                    pagination.showPage(page.page(), page.pageSize(), page.total());
                    books = List.copyOf(page.items());
                    DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0);
                    for (BookSummary book : page.items()) model.addRow(new Object[]{book.title(),
                            book.author(), book.category(), book.availableCopies() + "/" + book.totalCopies()});
                    status.setText(page.items().isEmpty() ? "未找到符合条件的馆藏，可调整关键词重试"
                            : "共 " + page.total() + " 条");
                    if (detail != null) detail.clearBook();
                    table.setEnabled(true);
                    for (int index = 0; index < books.size(); index++) {
                        if (books.get(index).bookId().equals(selectedBookId)) {
                            int row = table.convertRowIndexToView(index);
                            table.setRowSelectionInterval(row, row);
                            break;
                        }
                    }
                }));
    }

    private BookSearchField selectedField() {
        return BookSearchField.values()[field.getSelectedIndex()];
    }

    private void loadSelectedDetail() {
        if (!table.isEnabled()) return;
        int row = table.getSelectedRow();
        if (detail == null || row < 0 || row >= books.size()) return;
        BookSummary book = books.get(table.convertRowIndexToModel(row));
        long request = catalogRequest;
        long selection = ++detailSequence;
        status.setText("正在加载图书详情……");
        service.getBook(book.bookId()).whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request) || selection != detailSequence) return;
            if (failure != null) LibraryFeedback.failure(this, status, failure, "图书详情加载失败，请重试。");
            else { detail.showBook(result); status.setText("图书详情已加载"); }
        }));
    }
}
