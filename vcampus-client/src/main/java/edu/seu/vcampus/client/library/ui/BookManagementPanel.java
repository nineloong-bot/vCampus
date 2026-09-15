package edu.seu.vcampus.client.library.ui;
import edu.seu.vcampus.client.library.service.LibraryClientService;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import java.util.Objects;
import java.awt.*;
import java.util.List;
import javax.swing.table.DefaultTableModel;
public final class BookManagementPanel extends LibraryDataPanel {
    private final LibraryClientService service;
    private final JTextField keyword = new JTextField(18);
    private final JComboBox<String> field = new JComboBox<>(new String[]{
            "全部栏目", "书名", "作者", "ISBN", "分类", "出版社"});
    private CopyManagementPanel copiesPanel;
    private final EmbeddedEditorHost editorHost;
    private boolean refreshing;
    private List<BookSummary> books = List.of();
    public BookManagementPanel(LibraryClientService service) {
        super("library.book-management", "书目管理", "选择左侧书目，在右侧管理馆藏副本。", "ISBN", "书名", "作者", "状态");
        this.service = Objects.requireNonNull(service, "service");
        JButton refresh = new JButton("搜索书目"); JButton create = new JButton("新增书目");
        JButton edit = new JButton("编辑所选");
        refresh.addActionListener(event -> refresh());
        create.addActionListener(event -> openCreateDialog());
        edit.addActionListener(event -> editSelected());
        JPanel actions = new JPanel(new GridLayout(0, 1)); actions.setOpaque(false);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT)); filters.setOpaque(false);
        keyword.setColumns(10);
        filters.add(keyword); filters.add(field); filters.add(refresh);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); buttons.setOpaque(false);
        buttons.add(edit); buttons.add(create);
        actions.add(filters); actions.add(buttons); add(actions, BorderLayout.SOUTH);
        editorHost = installEditorHost();
        keyword.addActionListener(event -> refresh());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing && copiesPanel != null)
                copiesPanel.selectBook(selectedBook());
        });
    }

    public void connectCopies(CopyManagementPanel copies) { copiesPanel = copies; }

    private BookSummary selectedBook() {
        int row = table.getSelectedRow();
        return row < 0 ? null : books.get(table.convertRowIndexToModel(row));
    }

    public void create(CreateBookCommand command) {
        create(command, null);
    }
    private void create(CreateBookCommand command, BookEditorPanel expected) {
        long request = beginMutation();
        status.setText("正在新增书目……");
        service.createBook(command).whenComplete((book, failure) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsMutation(request)) return;
            if (failure == null) {
                status.setText("书目已新增"); close(expected); mutationSucceeded();
            }
            else LibraryFeedback.failure(this, status, failure, "新增书目失败，请检查输入后重试。");
        }));
    }
    public void update(UpdateBookCommand command) {
        update(command, null);
    }
    private void update(UpdateBookCommand command, BookEditorPanel expected) {
        long request = beginMutation();
        status.setText("正在保存书目……");
        service.updateBook(command).whenComplete((book, failure) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsMutation(request)) return;
            if (failure != null) {
                LibraryFeedback.failure(this, status, failure, "书目保存失败，请刷新后重试。");
                return;
            }
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            for (int index = 0; index < books.size(); index++) {
                BookSummary summary = books.get(index);
                if (!summary.bookId().equals(book.bookId())) continue;
                BookSummary changed = new BookSummary(book.bookId(), book.isbn(), book.title(),
                        book.author(), book.category(), summary.availableCopies(),
                        summary.totalCopies(), book.active());
                java.util.ArrayList<BookSummary> updated = new java.util.ArrayList<>(books);
                updated.set(index, changed); books = List.copyOf(updated);
                model.setValueAt(book.isbn(), index, 0); model.setValueAt(book.title(), index, 1);
                model.setValueAt(book.author(), index, 2);
                model.setValueAt(book.active() ? "已启用" : "已停用", index, 3);
                break;
            }
            status.setText("书目已保存");
            close(expected);
            mutationSucceeded();
        }));
    }

    public void refresh() {
        long request = beginRequest(); status.setText("正在加载书目……");
        service.searchManagedBooks(new BookSearchQuery(keyword.getText().trim(),
                BookSearchField.values()[field.getSelectedIndex()], null, false, 1, 100)).whenComplete((page, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (!accepts(request)) return;
                    if (failure != null) { LibraryFeedback.failure(this, status, failure, "书目加载失败，请重试。"); return; }
                    BookSummary selected = selectedBook();
                    refreshing = true;
                    books = List.copyOf(page.items()); DefaultTableModel model = (DefaultTableModel) table.getModel();
                    model.setRowCount(0); for (BookSummary book : books)
                        model.addRow(new Object[]{book.isbn(), book.title(), book.author(),
                                book.active() ? "已启用" : "已停用"});
                    if (!books.isEmpty()) {
                        int index = 0;
                        for (int i = 0; i < books.size(); i++)
                            if (selected != null && selected.bookId().equals(books.get(i).bookId())) index = i;
                        int row = table.convertRowIndexToView(index);
                        table.setRowSelectionInterval(row, row);
                    }
                    refreshing = false;
                    if (copiesPanel != null) copiesPanel.selectBook(selectedBook());
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
                    if (failure != null) LibraryFeedback.failure(this, status, failure, "书目详情加载失败，请重试。");
                    else {
                        editorHost.showEditor(editor(book));
                    }
                }));
    }

    private void openCreateDialog() {
        editorHost.showEditor(editor(null));
    }

    private BookEditorPanel editor(BookDetail book) {
        BookEditorPanel[] expected = new BookEditorPanel[1];
        BookFormCardPanel form = new BookFormCardPanel(
                command -> create(command, expected[0]),
                command -> update(command, expected[0]),
                () -> editorHost.requestClose(expected[0]));
        BookEditorPanel result = new BookEditorPanel(form);
        expected[0] = result;
        if (book == null) form.prepareCreate(); else form.prepareEdit(book);
        return result;
    }

    private void close(BookEditorPanel expected) {
        if (expected == null) editorHost.completeAndClose();
        else editorHost.completeAndClose(expected);
    }
}
