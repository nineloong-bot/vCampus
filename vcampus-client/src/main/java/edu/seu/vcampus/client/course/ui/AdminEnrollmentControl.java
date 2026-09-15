package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.AdminEnrollStudentCommand;
import edu.seu.vcampus.common.course.CourseStudentCandidate;
import edu.seu.vcampus.common.course.CourseStudentCandidateQuery;
import edu.seu.vcampus.common.course.OfferingSummary;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Embedded administrator control for finding and placing a student in a teaching class. */
final class AdminEnrollmentControl extends JPanel {
    private final CourseUiGateway gateway;
    private final OfferingSummary offering;
    private final Runnable onSuccess;
    private final Runnable onCancel;
    private final Consumer<String> onError;
    private final JTextField studentNumber = new JTextField(14);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"学号", "姓名", "班级"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable candidates = new JTable(model);
    private final JLabel state = new JLabel("输入学号后选择学生");
    private final JButton submit = AbstractCoursePanel.secondary("确认添加");
    private final Timer searchTimer = new Timer(250, event -> search());
    private final AtomicLong requestSequence = new AtomicLong();
    private boolean selectingCandidate;
    private boolean active;

    AdminEnrollmentControl(CourseUiGateway gateway, OfferingSummary offering,
                           Runnable onSuccess, Runnable onCancel, Consumer<String> onError) {
        super(new BorderLayout(0, UiSpacing.SM));
        this.gateway = Objects.requireNonNull(gateway);
        this.offering = Objects.requireNonNull(offering);
        this.onSuccess = Objects.requireNonNull(onSuccess);
        this.onCancel = Objects.requireNonNull(onCancel);
        this.onError = Objects.requireNonNull(onError);
        setOpaque(false);
        setPreferredSize(new Dimension(520, 230));
        searchTimer.setRepeats(false);
        add(inputRow(), BorderLayout.NORTH);
        add(candidateList(), BorderLayout.CENTER);
        add(actionRow(), BorderLayout.SOUTH);
        listenForSearch();
    }

    boolean isDirty() { return !studentNumber.getText().isBlank(); }
    void activate() { active = true; }
    void deactivate() { active = false; searchTimer.stop(); requestSequence.incrementAndGet(); }

    private JPanel inputRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SM, 0));
        row.setOpaque(false);
        JLabel label = new JLabel("学生学号");
        label.setFont(UiTypography.BODY);
        studentNumber.setFont(UiTypography.BODY);
        studentNumber.setPreferredSize(new Dimension(180, UiDimensions.CONTROL_HEIGHT));
        studentNumber.getAccessibleContext().setAccessibleName("学生学号");
        row.add(label);
        row.add(studentNumber);
        row.add(state);
        return row;
    }

    private JScrollPane candidateList() {
        candidates.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        candidates.setRowHeight(UiDimensions.TABLE_ROW_HEIGHT);
        candidates.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        candidates.getSelectionModel().addListSelectionListener(event -> selectCandidate());
        JScrollPane scroll = new JScrollPane(candidates);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        scroll.setPreferredSize(new Dimension(500, 140));
        return scroll;
    }

    private JPanel actionRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SM, 0));
        row.setOpaque(false);
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> onCancel.run());
        submit.getAccessibleContext().setAccessibleName("确认添加学生");
        submit.addActionListener(event -> submit());
        row.add(cancel);
        row.add(submit);
        return row;
    }

    private void listenForSearch() {
        studentNumber.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() {
                if (!selectingCandidate) searchTimer.restart();
            }
        });
    }

    private void search() {
        String number = studentNumber.getText().trim();
        if (number.isEmpty()) {
            showCandidates(List.of(), "输入学号后选择学生");
            return;
        }
        long request = requestSequence.incrementAndGet();
        state.setText("正在检索…");
        gateway.searchStudentCandidates(new CourseStudentCandidateQuery(number, 0, 20))
                .whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
                    if (!active || request != requestSequence.get()) return;
                    if (error != null) {
                        showCandidates(List.of(), "学生检索失败");
                    } else {
                        showCandidates(page.items(), page.items().isEmpty() ? "未找到学生" : "请选择学生");
                    }
                }));
    }

    private void showCandidates(List<CourseStudentCandidate> rows, String message) {
        model.setRowCount(0);
        for (CourseStudentCandidate row : rows) {
            model.addRow(new Object[]{row.studentNumber(), row.studentName(),
                    row.className() == null ? "—" : row.className()});
        }
        state.setText(message);
    }

    private void selectCandidate() {
        int row = candidates.getSelectedRow();
        if (row < 0) return;
        selectingCandidate = true;
        studentNumber.setText(String.valueOf(model.getValueAt(row, 0)));
        selectingCandidate = false;
    }

    private void submit() {
        String number = studentNumber.getText().trim();
        if (number.isEmpty()) {
            onError.accept("请输入学生学号");
            return;
        }
        submit.setEnabled(false);
        gateway.adminEnrollStudent(new AdminEnrollStudentCommand(number, offering.offeringId()))
                .whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
                    if (!active) return;
                    submit.setEnabled(true);
                    if (error != null) {
                        onError.accept("添加失败，请核对学号、修读状态和教学班状态");
                        return;
                    }
                    selectingCandidate = true;
                    studentNumber.setText("");
                    selectingCandidate = false;
                    showCandidates(List.of(), "输入学号后选择学生");
                    onSuccess.run();
                }));
    }
}
