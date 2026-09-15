package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Wide embedded workspace for CSV preview and balanced class assignment. */
public final class BatchClassAssignmentPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
    private final StudentClientService students;
    private final MajorView major;
    private final Consumer<BatchImportResult> completed;
    private final Runnable close;
    private final BatchAssignmentTableModel model;
    private final JLabel file = new JLabel("未选择文件");
    private final JLabel status = new JLabel(" ");
    private final JButton assign = new JButton("自动分配");
    private final JButton submit = new JButton("确认导入");
    private long generation;

    /** Creates a batch editor for one major and at least two destination classes. */
    public BatchClassAssignmentPanel(StudentClientService students, MajorView major,
            List<ClassView> classes, Consumer<BatchImportResult> completed, Runnable close) {
        this.students = Objects.requireNonNull(students); this.major = Objects.requireNonNull(major);
        this.completed = Objects.requireNonNull(completed); this.close = Objects.requireNonNull(close);
        model = new BatchAssignmentTableModel(classes); build(classes);
    }

    private void build(List<ClassView> classes) {
        root.setName("student.batch.editor"); root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(UiBorders.pageInset());
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        fileRow.setOpaque(false); JButton choose = new JButton("选择CSV文件");
        choose.addActionListener(event -> chooseFile()); fileRow.add(choose); fileRow.add(file);
        root.add(fileRow, BorderLayout.NORTH);
        JTable table = new JTable(model); table.setName("student.batch.table"); table.setRowHeight(28);
        JComboBox<String> classChoices = new JComboBox<>(classes.stream()
                .map(value -> value.code() + " - " + value.name()).toArray(String[]::new));
        table.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(classChoices));
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        bottom.setOpaque(false); status.setName("student.batch.error"); status.setForeground(UiColors.ERROR_FG);
        bottom.add(status, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.setOpaque(false);
        assign.setEnabled(false); assign.addActionListener(event -> autoAssign());
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        submit.setEnabled(false); submit.addActionListener(event -> submit());
        actions.add(assign); actions.add(cancel); actions.add(submit); bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV文件 (*.csv)", "csv"));
        if (chooser.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        try {
            var rows = BatchStudentCsv.parse(selected); model.setRows(rows);
            file.setText(selected.getName() + " (" + rows.size() + " 条记录)");
            assign.setEnabled(model.hasRows()); submit.setEnabled(false); status.setText(" ");
        } catch (Exception failure) {
            status.setText("CSV解析失败: " + failure.getMessage());
        }
    }

    private void autoAssign() {
        model.autoAssign(); submit.setEnabled(model.hasRows()); status.setText(model.summary());
    }
    private void submit() {
        long request = ++generation; assign.setEnabled(false); submit.setEnabled(false);
        status.setText("正在导入…");
        students.batchImport(model.command(major.majorId()))
                .whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
                    if (request != generation) return;
                    if (failure != null || body == null || !body.success() || body.data() == null) {
                        assign.setEnabled(true); submit.setEnabled(true);
                        status.setText(body != null && body.message() != null
                                ? body.message() : "导入失败，请稍后重试"); return;
                    }
                    completed.accept(body.data()); close.run();
                }));
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() { return model.hasRows(); }
    @Override public void onClosed() { generation++; }
}
