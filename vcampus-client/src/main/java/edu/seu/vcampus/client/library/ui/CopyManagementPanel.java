package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import java.util.Objects;
import java.awt.*;
import java.util.List;
import javax.swing.table.DefaultTableModel;
public final class CopyManagementPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField bookId = new JTextField(12);
    private List<BookCopyView> copies = List.of();
    public CopyManagementPanel(LibraryClientService service) {
        super("library.copy-management", "副本管理", "维护馆藏条码、位置与状态。", "条码", "书目", "位置", "状态", "操作");
        this.service = Objects.requireNonNull(service, "service");
        JButton load = new JButton("加载副本"); JButton add = new JButton("新增副本");
        JButton change = new JButton("变更所选状态");
        load.addActionListener(event -> loadCopies());
        add.addActionListener(event -> openAddDialog()); change.addActionListener(event -> openStatusDialog());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(new JLabel("书目 ID")); actions.add(bookId); actions.add(load); actions.add(add); actions.add(change);
        add(actions, BorderLayout.SOUTH);
    }
    public void add(AddBookCopyCommand command) {
        long request = beginRequest();
        status.setText("正在新增馆藏副本……");
        service.addCopy(command).whenComplete((copy, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure == null) status.setText("馆藏副本已新增");
            else LibraryFeedback.failure(this, status, failure, "新增副本失败，请检查输入后重试。");
        }));
    }
    public void changeStatus(ChangeCopyStatusCommand command) {
        long request = beginRequest();
        status.setText("正在更新副本状态……");
        service.changeCopyStatus(command).whenComplete((copy, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure == null) status.setText("副本状态已更新");
            else LibraryFeedback.failure(this, status, failure, "副本状态更新失败，请刷新后重试。");
        }));
    }

    public void loadCopies() {
        String selectedBook = bookId.getText().trim();
        if (selectedBook.isEmpty()) { status.setText("请先输入书目 ID"); return; }
        long request = beginRequest(); status.setText("正在加载馆藏副本……");
        service.getBook(selectedBook).whenComplete((book, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure != null) { LibraryFeedback.failure(this, status, failure, "副本加载失败，请检查书目 ID。"); return; }
            copies = book.copies(); DefaultTableModel model = (DefaultTableModel) table.getModel(); model.setRowCount(0);
            for (BookCopyView copy : copies) model.addRow(new Object[]{copy.barcode(), book.title(), copy.locationCode(), copy.status(), "选择后变更"});
            status.setText(copies.isEmpty() ? "该书目暂无馆藏副本" : "共 " + copies.size() + " 个馆藏副本");
        }));
    }

    private void openAddDialog() {
        JTextField barcode = new JTextField(), location = new JTextField();
        JPanel form = form(new String[]{"馆藏条码", "馆藏位置"}, new JComponent[]{barcode, location});
        if (JOptionPane.showConfirmDialog(this, form, "新增馆藏副本", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION)
            add(new AddBookCopyCommand(bookId.getText().trim(), barcode.getText().trim(), location.getText().trim()));
    }

    private void openStatusDialog() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= copies.size()) { status.setText("请先选择一个馆藏副本"); return; }
        BookCopyView copy = copies.get(table.convertRowIndexToModel(row));
        JComboBox<CopyStatus> state = new JComboBox<>(CopyStatus.values());
        state.setSelectedItem(copy.status());
        JPanel form = form(new String[]{"馆藏条码", "目标状态"}, new JComponent[]{new JLabel(copy.barcode()), state});
        if (JOptionPane.showConfirmDialog(this, form, "变更副本状态", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        changeStatus(new ChangeCopyStatusCommand(copy.copyId(), (CopyStatus) state.getSelectedItem(), copy.rowVersion()));
    }

    private static JPanel form(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int index = 0; index < labels.length; index++) { panel.add(new JLabel(labels[index])); panel.add(fields[index]); }
        return panel;
    }
}
