package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferBatchCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** Manages only school-wide transfer batches for the central student administrator. */
public final class MajorTransferBatchManagementPanel extends JPanel {
    private final StudentClientService students;
    private final DefaultListModel<MajorTransferBatchView> model = new DefaultListModel<>();
    private final JList<MajorTransferBatchView> batches = new JList<>(model);
    private final JLabel status = new JLabel(" ");

    /** Creates the global transfer-batch workspace. */
    public MajorTransferBatchManagementPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        setName("major-transfer.batch-management");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new EmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3,
                UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        build();
    }

    private void build() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setOpaque(false);
        JButton create = new JButton("新建批次");
        create.setName("saveBatchButton");
        create.addActionListener(event -> edit(null));
        JButton update = new JButton("编辑批次");
        update.setName("editBatchButton");
        update.addActionListener(event -> {
            if (batches.getSelectedValue() != null) edit(batches.getSelectedValue());
        });
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(event -> refresh());
        toolbar.add(create);
        toolbar.add(update);
        toolbar.add(refresh);
        batches.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        batches.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof MajorTransferBatchView batch) {
                    setText(batch.batchName() + " [" + batch.status() + "]");
                }
                return this;
            }
        });
        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(batches), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    @Override public void addNotify() {
        super.addNotify();
        refresh();
    }

    private void refresh() {
        students.listTransferBatches().whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) {
                        status.setText(message(response, "批次加载失败"));
                        return;
                    }
                    model.clear();
                    response.data().forEach(model::addElement);
                }));
    }

    private void edit(MajorTransferBatchView batch) {
        JTextField name = new JTextField(batch == null ? "" : batch.batchName(), 24);
        JComboBox<MajorTransferBatchStatus> batchStatus =
                new JComboBox<>(MajorTransferBatchStatus.values());
        batchStatus.setSelectedItem(batch == null
                ? MajorTransferBatchStatus.DRAFT : batch.status());
        JTextField start = date(batch == null ? Instant.now() : batch.applicationStart());
        JTextField end = date(batch == null ? Instant.now().plusSeconds(604800)
                : batch.applicationEnd());
        JTextField publicityStart = date(batch == null ? null : batch.publicityStart());
        JTextField publicityEnd = date(batch == null ? null : batch.publicityEnd());
        JTextField effective = date(batch == null ? null : batch.effectiveDate());
        JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
        addField(form, "批次名称", name);
        addField(form, "状态", batchStatus);
        addField(form, "报名开始", start);
        addField(form, "报名结束", end);
        addField(form, "公示开始", publicityStart);
        addField(form, "公示结束", publicityEnd);
        addField(form, "生效时间", effective);
        if (JOptionPane.showConfirmDialog(this, form, "转专业批次",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            var command = new SaveMajorTransferBatchCommand(
                    batch == null ? null : batch.batchId(), name.getText().trim(),
                    (MajorTransferBatchStatus) batchStatus.getSelectedItem(), parse(start),
                    parse(end), parse(publicityStart), parse(publicityEnd), parse(effective),
                    batch == null ? 0 : batch.rowVersion());
            students.saveTransferBatch(command).whenComplete((response, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        status.setText(response != null && response.success()
                                ? "保存成功" : message(response, "保存失败"));
                        if (response != null && response.success()) refresh();
                    }));
        } catch (RuntimeException error) {
            JOptionPane.showMessageDialog(this, "请检查字段：" + error.getMessage());
        }
    }

    private static JTextField date(Instant value) {
        var format = java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
        return new JTextField(value == null ? ""
                : format.format(value.atZone(ZoneId.of("Asia/Shanghai"))), 18);
    }

    private static Instant parse(JTextField field) {
        if (field.getText().isBlank()) return null;
        return java.time.LocalDateTime.parse(field.getText().trim(),
                java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"))
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant();
    }

    private static void addField(JPanel panel, String label, Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private static String message(ResponseBody<?> response, String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
