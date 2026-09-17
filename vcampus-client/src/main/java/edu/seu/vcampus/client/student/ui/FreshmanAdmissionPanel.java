package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.FreshmanAdmissionCommand;
import edu.seu.vcampus.common.student.FreshmanAdmissionPreview;
import edu.seu.vcampus.common.student.FreshmanAdmissionResult;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.time.Year;
import java.util.Objects;
import java.util.function.Consumer;

/** Embedded CSV workflow for previewing and atomically admitting freshmen. */
public final class FreshmanAdmissionPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final StudentClientService students;
    private final Consumer<FreshmanAdmissionResult> completed;
    private final Runnable close;
    private final JLabel status = new JLabel("请选择固定格式 CSV：姓名,性别,身份证,学院,专业");
    private final JButton preview = new JButton("预览分班");
    private final JButton submit = new JButton("确认录取");
    private final JSpinner year = new JSpinner(new SpinnerNumberModel(Year.now().getValue(), 2000, 2099, 1));
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] {"行", "姓名", "学院", "专业", "预分班"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private String csv;
    private FreshmanAdmissionCommand command;
    private long generation;

    /** Creates the freshman batch-admission workflow. */
    public FreshmanAdmissionPanel(StudentClientService students, Consumer<FreshmanAdmissionResult> completed,
                                  Runnable close) {
        this.students = Objects.requireNonNull(students); this.completed = Objects.requireNonNull(completed);
        this.close = Objects.requireNonNull(close); build();
    }

    private void build() {
        root.setName("student.freshman-admission.editor");
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton choose = new JButton("选择 CSV"); choose.addActionListener(event -> choose());
        preview.setEnabled(false); preview.addActionListener(event -> preview());
        submit.setEnabled(false); submit.addActionListener(event -> submit());
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        top.add(choose); top.add(new JLabel("入学年份：")); top.add(year); top.add(preview); top.add(submit); top.add(cancel);
        root.add(top, BorderLayout.NORTH); root.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
    }

    private void choose() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV 文件 (*.csv)", "csv"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        try {
            csv = Files.readString(chooser.getSelectedFile().toPath()); command = null; model.setRowCount(0);
            preview.setEnabled(true); submit.setEnabled(false); status.setText("已选择 " + chooser.getSelectedFile().getName());
        } catch (Exception error) { status.setText("读取 CSV 失败"); }
    }

    private void preview() {
        if (csv == null) return;
        command = new FreshmanAdmissionCommand(csv, (Integer) year.getValue());
        long request = ++generation; preview.setEnabled(false); submit.setEnabled(false); status.setText("正在校验与分班…");
        students.previewFreshmanAdmission(command).whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
            if (request != generation) return;
            preview.setEnabled(true);
            if (failure != null || body == null || !body.success() || body.data() == null) {
                command = null; status.setText(body == null ? "预览失败" : body.message()); return;
            }
            show(body.data()); submit.setEnabled(true);
        }));
    }

    private void show(FreshmanAdmissionPreview value) {
        model.setRowCount(0);
        value.assignments().forEach(assignment -> model.addRow(new Object[] {assignment.row().lineNumber(),
                assignment.row().name(), assignment.row().departmentName(), assignment.row().majorName(), assignment.className()}));
        status.setText("预览完成：" + value.assignments().size() + " 人；请确认后录取");
    }

    private void submit() {
        if (command == null) return;
        long request = ++generation; preview.setEnabled(false); submit.setEnabled(false); status.setText("正在原子录取…");
        students.admitFreshmen(command).whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
            if (request != generation) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                preview.setEnabled(true); submit.setEnabled(true); status.setText(body == null ? "录取失败" : body.message()); return;
            }
            completed.accept(body.data()); close.run();
        }));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return csv != null; }
    @Override public void onClosed() { generation++; }
}
