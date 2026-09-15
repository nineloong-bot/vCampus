package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferBatchView;
import edu.seu.vcampus.common.student.majortransfer.SaveMajorTransferBatchCommand;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** In-workspace card panel for creating and editing major transfer batches without popups. */
public final class MajorTransferBatchFormCardPanel extends JPanel {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final JLabel cardTitle = new JLabel("新建转专业批次");
    private final JTextField nameField = new JTextField(20);
    private final JComboBox<MajorTransferBatchStatus> statusCombo =
            new JComboBox<>(MajorTransferBatchStatus.values());
    private final JTextField startField = new JTextField(16);
    private final JTextField endField = new JTextField(16);
    private final JTextField pubStartField = new JTextField(16);
    private final JTextField pubEndField = new JTextField(16);
    private final JTextField effectiveField = new JTextField(16);
    private final JButton saveButton = new JButton("保存批次");
    private final JButton resetButton = new JButton("重置");
    private final JLabel feedbackLabel = new JLabel(" ");

    private MajorTransferBatchView currentBatch;

    /** Creates the in-workspace batch editor card. */
    public MajorTransferBatchFormCardPanel() {
        super(new BorderLayout(0, UiSpacing.SPACE_3));
        setOpaque(true);
        setBackground(UiColors.BACKGROUND_SUBTLE);
        setBorder(BorderFactory.createCompoundBorder(
                UiBorders.LINE,
                BorderFactory.createEmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_4,
                        UiSpacing.SPACE_3, UiSpacing.SPACE_4)));
        saveButton.setName("saveBatchButton");
        resetButton.setName("major-transfer.batch-reset");
        nameField.setName("major-transfer.batch-name");
        statusCombo.setName("major-transfer.batch-state");
        startField.setName("major-transfer.batch-application-start");
        endField.setName("major-transfer.batch-application-end");
        pubStartField.setName("major-transfer.batch-publicity-start");
        pubEndField.setName("major-transfer.batch-publicity-end");
        effectiveField.setName("major-transfer.batch-effective-time");
        feedbackLabel.setName("major-transfer.batch-feedback");
        statusCombo.setRenderer(new MajorTransferBatchStatusRenderer());
        resetButton.addActionListener(e -> resetToCurrent());
        buildLayout();
        clearForNew();
    }

    private void buildLayout() {
        cardTitle.setFont(UiTypography.SECTION_TITLE);
        cardTitle.setForeground(UiColors.TEXT_PRIMARY);
        add(cardTitle, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(UiSpacing.SPACE_1, UiSpacing.SPACE_1,
                UiSpacing.SPACE_1, UiSpacing.SPACE_1);
        int row = 0;
        addRow(form, c, row++, "批次名称 *", nameField);
        addRow(form, c, row++, "批次状态", statusCombo);
        addRow(form, c, row++, "报名开始 *", startField);
        addRow(form, c, row++, "报名结束 *", endField);
        addRow(form, c, row++, "公示开始", pubStartField);
        addRow(form, c, row++, "公示结束", pubEndField);
        addRow(form, c, row++, "生效时间", effectiveField);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        actions.setOpaque(false);
        saveButton.setBackground(UiColors.ACCENT);
        saveButton.setForeground(UiColors.TEXT_ON_PRIMARY);
        actions.add(resetButton);
        actions.add(saveButton);

        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        c.insets = new Insets(UiSpacing.SPACE_3, 0, 0, 0);
        form.add(actions, c);

        add(form, BorderLayout.CENTER);

        feedbackLabel.setFont(UiTypography.CAPTION);
        feedbackLabel.setForeground(UiColors.TEXT_SECONDARY);
        add(feedbackLabel, BorderLayout.SOUTH);
    }

    private static void addRow(JPanel p, GridBagConstraints c, int row, String lbl, Component cmp) {
        c.gridx = 0; c.gridy = row; c.gridwidth = 1; c.weightx = 0.3;
        JLabel l = new JLabel(lbl);
        l.setFont(UiTypography.BODY);
        p.add(l, c);
        c.gridx = 1; c.weightx = 0.7;
        p.add(cmp, c);
    }

    /** Prepares the form card for creating a new batch. */
    public void clearForNew() {
        this.currentBatch = null;
        cardTitle.setText("新建转专业批次");
        nameField.setText("");
        statusCombo.setSelectedItem(MajorTransferBatchStatus.DRAFT);
        Instant now = Instant.now();
        startField.setText(FMT.format(now.atZone(SHANGHAI)));
        endField.setText(FMT.format(now.plusSeconds(604800).atZone(SHANGHAI)));
        pubStartField.setText("");
        pubEndField.setText("");
        effectiveField.setText("");
        showFeedback(" ", false);
    }

    /** Populates the form card with an existing batch for editing. */
    public void loadBatch(MajorTransferBatchView batch) {
        this.currentBatch = batch;
        if (batch == null) {
            clearForNew();
            return;
        }
        cardTitle.setText("编辑转专业批次：" + batch.batchName());
        nameField.setText(batch.batchName());
        statusCombo.setSelectedItem(batch.status());
        startField.setText(formatDate(batch.applicationStart()));
        endField.setText(formatDate(batch.applicationEnd()));
        pubStartField.setText(formatDate(batch.publicityStart()));
        pubEndField.setText(formatDate(batch.publicityEnd()));
        effectiveField.setText(formatDate(batch.effectiveDate()));
        showFeedback(" ", false);
    }

    private void resetToCurrent() {
        if (currentBatch == null) clearForNew();
        else loadBatch(currentBatch);
    }

    /** Builds the save command or throws IllegalArgumentException with user message. */
    public SaveMajorTransferBatchCommand buildCommand() {
        String name = nameField.getText().trim();
        if (name.isBlank()) throw new IllegalArgumentException("批次名称不能为空");
        Instant start = parseDate(startField, "报名开始时间");
        Instant end = parseDate(endField, "报名结束时间");
        if (start == null || end == null) throw new IllegalArgumentException("报名开始与结束时间必填");
        if (end.isBefore(start)) throw new IllegalArgumentException("报名结束时间不得早于开始时间");
        Instant pubStart = parseDate(pubStartField, "公示开始时间");
        Instant pubEnd = parseDate(pubEndField, "公示结束时间");
        Instant eff = parseDate(effectiveField, "生效时间");
        return new SaveMajorTransferBatchCommand(
                currentBatch == null ? null : currentBatch.batchId(), name,
                (MajorTransferBatchStatus) statusCombo.getSelectedItem(),
                start, end, pubStart, pubEnd, eff,
                currentBatch == null ? 0 : currentBatch.rowVersion());
    }

    private static String formatDate(Instant instant) {
        return instant == null ? "" : FMT.format(instant.atZone(SHANGHAI));
    }

    private static Instant parseDate(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text, FMT).atZone(SHANGHAI).toInstant();
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " 格式错误，应为 yyyy-MM-dd HH:mm");
        }
    }

    /** Displays a message in the card feedback area. */
    public void showFeedback(String text, boolean isError) {
        feedbackLabel.setForeground(isError ? UiColors.ERROR_FG : UiColors.TEXT_SECONDARY);
        feedbackLabel.setText(text == null || text.isBlank() ? " " : text);
    }

    void setBusy(boolean busy) {
        JComponent[] controls = {nameField, statusCombo, startField, endField, pubStartField,
                pubEndField, effectiveField, saveButton, resetButton};
        for (JComponent control : controls) control.setEnabled(!busy);
    }

    /** Returns the save button to attach listeners. */
    public JButton saveButton() { return saveButton; }
}
