package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.editor.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Embedded editor for a college's transfer-admission option. */
final class MajorTransferOptionEditorPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JComboBox<DepartmentView> department = new JComboBox<>();
    private final JComboBox<MajorView> major = new JComboBox<>();
    private final JTextField grades = new JTextField("2025,2026");
    private final JSpinner receive = new JSpinner(new SpinnerNumberModel(10, 0, 10000, 1));
    private final JSpinner interview = new JSpinner(new SpinnerNumberModel(20, 0, 10000, 1));
    private final JSpinner weight = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
    private final JLabel status = new JLabel("正在加载学院…");
    private final Map<String, MajorTransferOptionView> optionsByMajor = new HashMap<>();
    private MajorTransferOptionView currentOption;
    private boolean updatingSelection;

    MajorTransferOptionEditorPanel(StudentClientService students, MajorTransferBatchView batch,
            Runnable completed, Runnable close) {
        Objects.requireNonNull(students); Objects.requireNonNull(batch);
        grades.setEditable(false);
        grades.setFocusable(false);
        grades.setToolTipText("转专业仅面向大一及大二学生（2025级、2026级）");
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        field(form, "学院", department); field(form, "专业", major);
        field(form, "允许入学年份", grades); field(form, "接收名额", receive);
        field(form, "面试名额", interview); field(form, "笔试权重%", weight);
        root.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消"); cancel.addActionListener(event -> close.run());
        JButton save = new JButton("保存");
        save.addActionListener(event -> save(students, batch, save, completed, close));
        actions.add(status); actions.add(cancel); actions.add(save); root.add(actions, BorderLayout.SOUTH);
        department.addActionListener(event -> loadMajors(students));
        major.addActionListener(event -> onMajorChanged());
        loadInitialData(students, batch);
    }

    private void loadInitialData(StudentClientService students, MajorTransferBatchView batch) {
        var optionsFuture = students.listTransferOptions(batch.batchId());
        if (optionsFuture != null) {
            optionsFuture.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                if (failure == null && response != null && response.success() && response.data() != null) {
                    optionsByMajor.clear();
                    response.data().forEach(opt -> optionsByMajor.put(opt.targetMajorId(), opt));
                    onMajorChanged();
                }
            }));
        }
        var deptsFuture = students.listDepartments(true);
        if (deptsFuture != null) {
            deptsFuture.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                updatingSelection = true;
                try {
                    department.removeAllItems();
                    if (failure != null || response == null || !response.success() || response.data().isEmpty()) {
                        status.setText("学院信息加载失败"); return;
                    }
                    response.data().forEach(department::addItem); status.setText(" ");
                } finally {
                    updatingSelection = false;
                }
                loadMajors(students);
            }));
        }
    }

    private void loadMajors(StudentClientService students) {
        if (updatingSelection) return;
        DepartmentView selected = (DepartmentView) department.getSelectedItem();
        if (selected == null) return;
        var majorsFuture = students.listMajors(selected.departmentId());
        if (majorsFuture != null) {
            majorsFuture.whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                updatingSelection = true;
                try {
                    major.removeAllItems();
                    if (failure != null || response == null || !response.success()) {
                        status.setText("专业信息加载失败"); return;
                    }
                    response.data().forEach(major::addItem); status.setText(" ");
                } finally {
                    updatingSelection = false;
                }
                onMajorChanged();
            }));
        }
    }

    private void onMajorChanged() {
        if (updatingSelection) return;
        MajorView selected = (MajorView) major.getSelectedItem();
        if (selected == null) { currentOption = null; return; }
        MajorTransferOptionView opt = optionsByMajor.get(selected.majorId());
        currentOption = opt;
        if (opt != null) {
            receive.setValue(opt.receiveQuota());
            interview.setValue(opt.interviewQuota());
            weight.setValue(opt.writtenWeightPct());
            if (opt.grades() != null && !opt.grades().isBlank()) grades.setText(opt.grades());
        } else {
            receive.setValue(10); interview.setValue(20); weight.setValue(60);
            grades.setText("2025,2026");
        }
    }

    private void save(StudentClientService students, MajorTransferBatchView batch, JButton button,
            Runnable completed, Runnable close) {
        MajorView selected = (MajorView) major.getSelectedItem();
        if (selected == null || grades.getText().isBlank()) { status.setText("请选择专业并填写入学年份"); return; }
        int written = (Integer) weight.getValue(); button.setEnabled(false); status.setText("正在保存…");
        String optionId = currentOption != null ? currentOption.optionId() : null;
        long version = currentOption != null ? currentOption.rowVersion() : 0;
        Double writtenPass = currentOption != null && currentOption.writtenPassScore() != null ? currentOption.writtenPassScore() : 60.0;
        Double interviewPass = currentOption != null && currentOption.interviewPassScore() != null ? currentOption.interviewPassScore() : 60.0;
        boolean exempt = currentOption != null && currentOption.difficultyQuotaExempt();
        String reqs = currentOption != null && currentOption.requirements() != null ? currentOption.requirements() : "";
        boolean active = currentOption == null || currentOption.active();
        var command = new SaveMajorTransferOptionCommand(optionId, batch.batchId(), selected.majorId(),
                grades.getText().trim(), (Integer) receive.getValue(), (Integer) interview.getValue(),
                writtenPass, interviewPass, written, 100 - written, exempt, reqs, active, version);
        students.saveTransferOption(command).whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure != null || response == null || !response.success()) {
                status.setText(response != null ? response.message() : "保存失败"); button.setEnabled(true); return;
            }
            completed.run(); close.run();
        }));
    }

    private static void field(JPanel panel, String label, Component component) {
        panel.add(new JLabel(label + "：")); panel.add(component);
    }
    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.WIDE; }
    @Override public boolean isDirty() {
        if (currentOption != null) {
            return !Objects.equals(grades.getText(), currentOption.grades())
                    || (Integer) receive.getValue() != currentOption.receiveQuota()
                    || (Integer) interview.getValue() != currentOption.interviewQuota()
                    || (Integer) weight.getValue() != currentOption.writtenWeightPct();
        }
        return !"2025,2026".equals(grades.getText()) || (Integer) receive.getValue() != 10
                || (Integer) interview.getValue() != 20 || (Integer) weight.getValue() != 60;
    }
}
