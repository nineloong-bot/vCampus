package edu.seu.vcampus.client.library.ui;

import edu.seu.vcampus.common.library.*;
import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Consumer;

/** In-workspace partitioned form card for adding and editing catalog books without popups. */
public final class BookFormCardPanel extends JPanel {
    private final JTextField isbn = new JTextField(14);
    private final JTextField title = new JTextField(14);
    private final JTextField author = new JTextField(14);
    private final JTextField publisher = new JTextField(14);
    private final JTextField publishDate = new JTextField(14);
    private final JTextField category = new JTextField(14);
    private final JTextArea description = new JTextArea(3, 14);
    private final JTextField location = new JTextField(14);
    private final JTextField barcode = new JTextField(14);
    private final JCheckBox active = new JCheckBox("启用书目", true);
    private final JLabel headerTitle = new JLabel("新增书目");
    private final JLabel feedback = new JLabel(" ");
    private final JButton saveButton = new JButton("保存书目");
    private final JButton cancelButton = new JButton("取消返回");
    private final JPanel copyConfigPanel = new JPanel(new GridLayout(0, 2, 8, 8));

    private Consumer<CreateBookCommand> onCreate;
    private Consumer<UpdateBookCommand> onUpdate;
    private Runnable onCancel;
    private BookDetail currentEditingBook;
    private String initialFingerprint = "";

    /** Creates an in-workspace book creation and editing card. */
    public BookFormCardPanel(Consumer<CreateBookCommand> onCreate,
                             Consumer<UpdateBookCommand> onUpdate,
                             Runnable onCancel) {
        super(new BorderLayout(0, 10));
        this.onCreate = Objects.requireNonNull(onCreate, "onCreate");
        this.onUpdate = Objects.requireNonNull(onUpdate, "onUpdate");
        this.onCancel = Objects.requireNonNull(onCancel, "onCancel");
        setName("library.book-form-card");
        isbn.setName("library.book-form.isbn"); title.setName("library.book-form.title");
        author.setName("library.book-form.author"); publisher.setName("library.book-form.publisher");
        publishDate.setName("library.book-form.publish-date"); category.setName("library.book-form.category");
        location.setName("library.book-form.location"); barcode.setName("library.book-form.barcode");
        setBackground(LibraryPalette.PAGE);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        buildLayout();
    }

    private void buildLayout() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        headerTitle.setFont(LibraryPalette.SECTION);
        header.add(headerTitle, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setOpaque(false);
        form.add(new JLabel("ISBN:")); form.add(isbn);
        form.add(new JLabel("书名:")); form.add(title);
        form.add(new JLabel("作者:")); form.add(author);
        form.add(new JLabel("出版社:")); form.add(publisher);
        form.add(new JLabel("出版日期(YYYY-MM-DD):")); form.add(publishDate);
        form.add(new JLabel("分类:")); form.add(category);
        form.add(new JLabel("状态:")); form.add(active);

        copyConfigPanel.setOpaque(false);
        copyConfigPanel.add(new JLabel("初始位置:")); copyConfigPanel.add(location);
        copyConfigPanel.add(new JLabel("初始条码:")); copyConfigPanel.add(barcode);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.add(form);
        center.add(copyConfigPanel);
        JPanel descPanel = new JPanel(new BorderLayout(4, 4));
        descPanel.setOpaque(false);
        descPanel.add(new JLabel("简介:"), BorderLayout.NORTH);
        descPanel.add(new JScrollPane(description), BorderLayout.CENTER);
        center.add(descPanel);
        add(new JScrollPane(center), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        feedback.setForeground(Color.RED);
        footer.add(feedback, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancelButton);
        buttons.add(saveButton);
        footer.add(buttons, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        saveButton.addActionListener(e -> submit());
        cancelButton.addActionListener(e -> onCancel.run());
    }

    /** Opens form in creation mode. */
    public void prepareCreate() {
        currentEditingBook = null;
        headerTitle.setText("新增书目与初始馆藏");
        isbn.setText(""); title.setText(""); author.setText(""); publisher.setText("");
        publishDate.setText("2026-01-01"); category.setText(""); description.setText("");
        location.setText(""); barcode.setText(""); active.setSelected(true); active.setVisible(false);
        copyConfigPanel.setVisible(true);
        feedback.setText(" ");
        initialFingerprint = fingerprint();
    }

    /** Opens form in update mode with selected book detail. */
    public void prepareEdit(BookDetail book) {
        currentEditingBook = book;
        headerTitle.setText("编辑书目：" + book.title());
        isbn.setText(book.isbn()); title.setText(book.title()); author.setText(book.author());
        publisher.setText(book.publisher()); publishDate.setText(book.publishDate().toString());
        category.setText(book.category()); description.setText(book.description());
        active.setSelected(book.active()); active.setVisible(true);
        copyConfigPanel.setVisible(false);
        feedback.setText(" ");
        initialFingerprint = fingerprint();
    }

    private void submit() {
        try {
            String isbnVal = isbn.getText().trim();
            String titleVal = title.getText().trim();
            String authorVal = author.getText().trim();
            String publisherVal = publisher.getText().trim();
            LocalDate dateVal = LocalDate.parse(publishDate.getText().trim());
            String catVal = category.getText().trim();
            String descVal = description.getText().trim();

            if (isbnVal.isEmpty() || titleVal.isEmpty() || authorVal.isEmpty()) {
                feedback.setText("请完整填写 ISBN、书名及作者");
                return;
            }
            if (currentEditingBook == null) {
                String loc = location.getText().trim();
                String bar = barcode.getText().trim();
                if (loc.isEmpty() || bar.isEmpty()) {
                    feedback.setText("请填写首个副本的位置与馆藏条码");
                    return;
                }
                onCreate.accept(new CreateBookCommand(isbnVal, titleVal, authorVal, publisherVal,
                        dateVal, catVal, descVal, loc, bar));
            } else {
                onUpdate.accept(new UpdateBookCommand(currentEditingBook.bookId(), isbnVal, titleVal,
                        authorVal, publisherVal, dateVal, catVal, descVal, active.isSelected(),
                        currentEditingBook.rowVersion()));
            }
        } catch (Exception ex) {
            feedback.setText("输入格式有误：" + ex.getMessage());
        }
    }

    private String fingerprint() {
        return String.join("\u0000", isbn.getText(), title.getText(), author.getText(),
                publisher.getText(), publishDate.getText(), category.getText(),
                description.getText(), location.getText(), barcode.getText(),
                Boolean.toString(active.isSelected()));
    }

    boolean hasChanges() { return !initialFingerprint.equals(fingerprint()); }
}
