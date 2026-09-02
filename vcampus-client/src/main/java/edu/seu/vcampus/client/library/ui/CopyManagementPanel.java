package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class CopyManagementPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField keyword = new JTextField(16);
    private List<BookCopyView> copies = List.of();
    private List<CopyRow> allCopies = List.of();

    public CopyManagementPanel(LibraryClientService service) {
        super("library.copy-management", "副本管理", "查看全部副本，并按书名、条码、位置或状态搜索。",
                "条码", "书目", "位置", "状态");
        this.service = Objects.requireNonNull(service, "service");
        JButton search = new JButton("搜索副本"), add = new JButton("新增副本"), change = new JButton("更新状态 / 找回");
        search.addActionListener(event -> filterCopies()); keyword.addActionListener(event -> filterCopies());
        add.addActionListener(event -> openAddDialog()); change.addActionListener(event -> openStatusDialog());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(new JLabel("关键词")); actions.add(keyword); actions.add(search); actions.add(add); actions.add(change);
        add(actions, BorderLayout.SOUTH);
    }

    public void add(AddBookCopyCommand command) {
        long request = beginRequest(); status.setText("正在新增馆藏副本……");
        service.addCopy(command).whenComplete((copy, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure == null) status.setText("馆藏副本已新增，请刷新查看");
            else LibraryFeedback.failure(this, status, failure, "新增副本失败，请检查输入后重试。");
        }));
    }

    public void changeStatus(ChangeCopyStatusCommand command) {
        long request = beginRequest(); status.setText("正在更新副本状态……");
        service.changeCopyStatus(command).whenComplete((copy, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure != null) {
                LibraryFeedback.failure(this, status, failure, "副本状态更新失败，请刷新后重试。");
                return;
            }
            allCopies = allCopies.stream().map(row -> row.copy().copyId().equals(copy.copyId())
                    ? new CopyRow(copy, row.title()) : row).toList();
            filterCopies();
            status.setText("副本状态已更新");
        }));
    }

    public void loadCopies() {
        long request = beginRequest(); status.setText("正在加载全部馆藏副本……");
        loadBookPages(1, new ArrayList<>()).thenCompose(books -> {
            List<CompletableFuture<BookDetail>> detailRequests = books.stream()
                    .map(book -> service.getBook(book.bookId())).toList();
            return CompletableFuture.allOf(detailRequests.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> detailRequests.stream().map(CompletableFuture::join)
                            .flatMap(detail -> detail.copies().stream().map(copy -> new CopyRow(copy, detail.title())))
                            .toList());
        }).whenComplete((rows, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure != null) { LibraryFeedback.failure(this, status, failure, "副本加载失败，请重试。"); return; }
            allCopies = List.copyOf(rows); filterCopies();
        }));
    }

    private CompletableFuture<List<BookSummary>> loadBookPages(int page, List<BookSummary> books) {
        return service.searchManagedBooks(new BookSearchQuery("", null, false, page, 100)).thenCompose(result -> {
            books.addAll(result.items());
            return books.size() < result.total() ? loadBookPages(page + 1, books)
                    : CompletableFuture.completedFuture(List.copyOf(books));
        });
    }

    private void filterCopies() {
        String query = keyword.getText().trim().toLowerCase(Locale.ROOT);
        List<CopyRow> visible = allCopies.stream().filter(row -> query.isEmpty()
                || row.title().toLowerCase(Locale.ROOT).contains(query)
                || row.copy().barcode().toLowerCase(Locale.ROOT).contains(query)
                || row.copy().locationCode().toLowerCase(Locale.ROOT).contains(query)
                || LibraryStatusText.copy(row.copy().status()).contains(query)).toList();
        copies = visible.stream().map(CopyRow::copy).toList();
        DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
        for (CopyRow row : visible) model.addRow(new Object[]{row.copy().barcode(), row.title(),
                row.copy().locationCode(), LibraryStatusText.copy(row.copy().status())});
        status.setText(visible.isEmpty() ? "未找到符合条件的副本" : "显示 " + visible.size() + " / " + allCopies.size() + " 个副本");
    }

    private void openAddDialog() {
        JTextField bookId = new JTextField(), barcode = new JTextField(), location = new JTextField();
        JPanel form = form(new String[]{"书目 ID", "馆藏条码", "馆藏位置"}, new JComponent[]{bookId, barcode, location});
        if (JOptionPane.showConfirmDialog(this, form, "新增馆藏副本", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION)
            add(new AddBookCopyCommand(bookId.getText().trim(), barcode.getText().trim(), location.getText().trim()));
    }

    private void openStatusDialog() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= copies.size()) { status.setText("请先选择一个馆藏副本"); return; }
        BookCopyView copy = copies.get(table.convertRowIndexToModel(row));
        if (copy.status() == CopyStatus.BORROWED) {
            status.setText("借出中的副本请在“借阅管理”中办理归还或标记遗失");
            return;
        }
        CopyStatus[] targets = copy.status() == CopyStatus.LOST
                ? new CopyStatus[]{CopyStatus.AVAILABLE, CopyStatus.DAMAGED}
                : copy.status() == CopyStatus.AVAILABLE
                        ? new CopyStatus[]{CopyStatus.DAMAGED}
                        : new CopyStatus[]{CopyStatus.AVAILABLE};
        JComboBox<CopyStatus> state = new JComboBox<>(targets);
        state.setRenderer((list, value, index, selected, focused) -> {
            JLabel label = (JLabel) new DefaultListCellRenderer().getListCellRendererComponent(
                    list, value, index, selected, focused);
            label.setText(value == null ? "" : LibraryStatusText.copy(value));
            return label;
        });
        JPanel form = form(new String[]{"馆藏条码", "目标状态"}, new JComponent[]{new JLabel(copy.barcode()), state});
        String title = copy.status() == CopyStatus.LOST ? "登记遗失副本已找回" : "变更副本状态";
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        changeStatus(new ChangeCopyStatusCommand(copy.copyId(), (CopyStatus) state.getSelectedItem(), copy.rowVersion()));
    }

    private static JPanel form(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int index = 0; index < labels.length; index++) { panel.add(new JLabel(labels[index])); panel.add(fields[index]); }
        return panel;
    }

    private record CopyRow(BookCopyView copy, String title) { }
}
