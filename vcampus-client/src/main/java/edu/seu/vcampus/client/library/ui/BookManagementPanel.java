package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import java.util.Objects;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import javax.swing.table.DefaultTableModel;
public final class BookManagementPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private List<BookSummary> books = List.of();
    public BookManagementPanel(LibraryClientService service) {
        super("library.book-management", "书目管理", "新增或维护书目元数据。", "ISBN", "书名", "作者", "状态", "操作");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("查询书目"); JButton create = new JButton("新增书目");
        JButton edit = new JButton("编辑所选");
        refresh.addActionListener(event -> refresh());
        create.addActionListener(event -> openCreateDialog());
        edit.addActionListener(event -> editSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        actions.add(refresh); actions.add(edit); actions.add(create); add(actions, BorderLayout.SOUTH);
    }
    public void create(CreateBookCommand command) {
        long request = beginRequest();
        status.setText("正在新增书目……");
        service.createBook(command).whenComplete((book, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure == null) status.setText("书目已新增");
            else LibraryFeedback.failure(this, status, failure, "新增书目失败，请检查输入后重试。");
        }));
    }
    public void update(UpdateBookCommand command) {
        long request = beginRequest();
        status.setText("正在保存书目……");
        service.updateBook(command).whenComplete((book, failure) -> SwingUtilities.invokeLater(() -> {
            if (!accepts(request)) return;
            if (failure == null) status.setText("书目已保存");
            else LibraryFeedback.failure(this, status, failure, "书目保存失败，请刷新后重试。");
        }));
    }

    public void refresh() {
        long request = beginRequest(); status.setText("正在加载书目……");
        service.searchBooks(new BookSearchQuery("", null, false, 1, 100)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) { LibraryFeedback.failure(this, status, failure, "书目加载失败，请重试。"); return; }
                    books = List.copyOf(page.items()); DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0); for (BookSummary book : books)
                        model.addRow(new Object[]{book.isbn(), book.title(), book.author(), "已启用", "选择后编辑"});
                    status.setText(books.isEmpty() ? "暂无书目，可新增第一条书目" : "共 " + page.total() + " 条书目");
                }));
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= books.size()) { status.setText("请先选择一本书目"); return; }
        long request = beginRequest(); status.setText("正在加载书目详情……");
        service.getBook(books.get(table.convertRowIndexToModel(row)).bookId()).whenComplete((book, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) LibraryFeedback.failure(this, status, failure, "书目详情加载失败，请重试。"); else openUpdateDialog(book);
                }));
    }

    private void openUpdateDialog(BookDetail book) {
        JTextField isbn = new JTextField(book.isbn()), title = new JTextField(book.title());
        JTextField author = new JTextField(book.author()), publisher = new JTextField(book.publisher());
        JTextField publishDate = new JTextField(book.publishDate().toString()), category = new JTextField(book.category());
        JTextArea description = new JTextArea(book.description(), 3, 24); JCheckBox active = new JCheckBox("启用", book.active());
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        String[] labels = {"ISBN", "书名", "作者", "出版社", "出版日期", "分类"};
        JComponent[] fields = {isbn, title, author, publisher, publishDate, category};
        for (int i = 0; i < labels.length; i++) { form.add(new JLabel(labels[i])); form.add(fields[i]); }
        form.add(new JLabel("简介")); form.add(new JScrollPane(description)); form.add(new JLabel("状态")); form.add(active);
        if (JOptionPane.showConfirmDialog(this, form, "编辑书目", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try { update(new UpdateBookCommand(book.bookId(), isbn.getText().trim(), title.getText().trim(),
                author.getText().trim(), publisher.getText().trim(), LocalDate.parse(publishDate.getText().trim()),
                category.getText().trim(), description.getText().trim(), active.isSelected(), book.rowVersion())); }
        catch (RuntimeException failure) { status.setText("请输入有效的书目信息和日期"); }
    }

    private void openCreateDialog() {
        JTextField isbn = new JTextField(), title = new JTextField(), author = new JTextField();
        JTextField publisher = new JTextField(), publishDate = new JTextField("2026-01-01"), category = new JTextField();
        JTextArea description = new JTextArea(3, 24);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("ISBN")); form.add(isbn); form.add(new JLabel("书名")); form.add(title);
        form.add(new JLabel("作者")); form.add(author); form.add(new JLabel("出版社")); form.add(publisher);
        form.add(new JLabel("出版日期（YYYY-MM-DD）")); form.add(publishDate);
        form.add(new JLabel("分类")); form.add(category); form.add(new JLabel("简介")); form.add(new JScrollPane(description));
        if (JOptionPane.showConfirmDialog(this, form, "新增书目", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            create(new CreateBookCommand(isbn.getText().trim(), title.getText().trim(), author.getText().trim(),
                    publisher.getText().trim(), LocalDate.parse(publishDate.getText().trim()),
                    category.getText().trim(), description.getText().trim()));
        } catch (RuntimeException failure) { status.setText("请输入有效的书目信息和日期"); }
    }
}
