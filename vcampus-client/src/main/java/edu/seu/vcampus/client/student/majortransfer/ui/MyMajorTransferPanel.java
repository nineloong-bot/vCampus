package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Student major-transfer workspace panel. */
public final class MyMajorTransferPanel extends JPanel {
    private static final Color TABLE_BORDER = new Color(178, 218, 211);
    private static final Color TABLE_LABEL = new Color(239, 247, 245);
    private static final Color ACTION_GREEN = new Color(139, 195, 74);
    private static final Color ACTION_RED = new Color(244, 67, 54);

    private final StudentClientService students;
    private final ClientConnection connection;
    private final AtomicLong generation = new AtomicLong();

    private JLabel statusLabel, errorLabel;
    private JButton refreshButton, saveButton, submitButton, withdrawButton, uploadButton;
    private JPanel eligibilityPanel, applicationPanel, timelinePanel;
    private JPanel infoPanel;
    private boolean busy;
    private JComboBox<OptionItem> targetMajorCombo;
    private JComboBox<String> applicationTypeCombo;
    private JTextArea reasonArea;
    private JPanel attachmentsPanel;
    private volatile boolean active;
    private MajorTransferWorkspace workspace;
    private String currentApplicationId;

    public MyMajorTransferPanel(StudentClientService students, ClientConnection connection) {
        super(new BorderLayout(0, UiSpacing.SPACE_4));
        this.students = Objects.requireNonNull(students);
        this.connection = Objects.requireNonNull(connection);
        setName("major-transfer.student");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.pageInset());
        build();
        connection.addStateListener(this::connectionChanged);
    }

    private void build() {
        JPanel top = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        top.setOpaque(false);
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("转专业申请");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        statusLabel = new JLabel("正在加载");
        statusLabel.setName("major-transfer.student.status");
        statusLabel.setFont(UiTypography.CAPTION);
        statusLabel.setForeground(UiColors.TEXT_SECONDARY);
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(UiSpacing.SPACE_1));
        titleBox.add(statusLabel);
        top.add(titleBox);
        refreshButton = new JButton("刷新");
        refreshButton.setName("major-transfer.student.refresh");
        refreshButton.addActionListener(e -> refresh());
        top.add(refreshButton, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        // Eligibility section
        content.add(sectionHeader("资格检查"));
        eligibilityPanel = new JPanel();
        eligibilityPanel.setLayout(new BoxLayout(eligibilityPanel, BoxLayout.Y_AXIS));
        eligibilityPanel.setOpaque(false);
        eligibilityPanel.setName("major-transfer.student.eligibility");
        content.add(eligibilityPanel);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_4));

        // Current info
        content.add(sectionHeader("当前信息"));
        infoPanel = new JPanel(new GridLayout(0, 2, UiSpacing.SPACE_2, UiSpacing.SPACE_1));
        infoPanel.setOpaque(false);
        content.add(infoPanel);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_4));

        // Application form
        content.add(sectionHeader("申请信息"));
        JPanel formPanel = buildFormPanel();
        content.add(formPanel);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_4));

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        buttons.setOpaque(false);
        saveButton = new JButton("暂存");
        saveButton.setName("major-transfer.student.save");
        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> saveDraft());
        buttons.add(saveButton);
        submitButton = new JButton("提交");
        submitButton.setName("major-transfer.student.submit");
        submitButton.setEnabled(false);
        submitButton.addActionListener(e -> submit());
        buttons.add(submitButton);
        withdrawButton = new JButton("撤回");
        withdrawButton.setName("major-transfer.student.withdraw");
        withdrawButton.setVisible(false);
        withdrawButton.addActionListener(e -> withdraw());
        buttons.add(withdrawButton);
        content.add(buttons);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_4));

        // Attachments
        content.add(sectionHeader("附件"));
        JPanel attachButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        attachButtons.setOpaque(false);
        uploadButton = new JButton("上传附件");
        uploadButton.setName("major-transfer.student.upload");
        uploadButton.setEnabled(false);
        uploadButton.addActionListener(e -> uploadAttachment());
        attachButtons.add(uploadButton);
        content.add(attachButtons);
        attachmentsPanel = new JPanel();
        attachmentsPanel.setLayout(new BoxLayout(attachmentsPanel, BoxLayout.Y_AXIS));
        attachmentsPanel.setOpaque(false);
        attachmentsPanel.setName("major-transfer.student.attachments");
        content.add(attachmentsPanel);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_4));

        // Timeline
        content.add(sectionHeader("审核进度"));
        timelinePanel = new JPanel();
        timelinePanel.setLayout(new BoxLayout(timelinePanel, BoxLayout.Y_AXIS));
        timelinePanel.setOpaque(false);
        timelinePanel.setName("major-transfer.student.timeline");
        content.add(timelinePanel);

        // Error label
        errorLabel = new JLabel(" ");
        errorLabel.setName("major-transfer.student.error");
        errorLabel.setForeground(ACTION_RED);
        errorLabel.setFont(UiTypography.CAPTION);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        JScrollPane scroll = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        center.add(scroll, BorderLayout.CENTER);
        center.add(errorLabel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

    }

    @Override public void addNotify() { super.addNotify(); active = true; refresh(); }
    @Override public void removeNotify() { active = false; generation.incrementAndGet(); super.removeNotify(); }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 4);
        int row = 0;

        gbc.gridx = 0; gbc.gridy = row;
        panel.add(label("目标专业:"), gbc);
        targetMajorCombo = new JComboBox<>();
        targetMajorCombo.setName("major-transfer.student.target-major");
        targetMajorCombo.addActionListener(e -> updateSubmitState());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(targetMajorCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(label("申请类型:"), gbc);
        applicationTypeCombo = new JComboBox<>(new String[]{"普通转专业", "学困生转专业"});
        applicationTypeCombo.setName("major-transfer.student.application-type");
        applicationTypeCombo.addActionListener(e -> updateSubmitState());
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1;
        panel.add(applicationTypeCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(label("申请理由:"), gbc);
        reasonArea = new JTextArea(4, 30);
        reasonArea.setName("major-transfer.student.reason");
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setToolTipText("填写申请理由，最多2000字；修改后请先暂存再提交");
        reasonArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSubmitState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSubmitState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSubmitState(); }
        });
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1; gbc.weighty = 1;
        panel.add(new JScrollPane(reasonArea), gbc);

        return panel;
    }

    private void refresh() {
        if (!active) return;
        long gen = generation.incrementAndGet();
        statusLabel.setText("正在加载...");
        errorLabel.setText(" ");
        students.getTransferWorkspace().whenComplete((response, error) -> {
            if (generation.get() != gen || !active) return;
            SwingUtilities.invokeLater(() -> {
                if (generation.get() != gen || !active) return;
                if (response != null && response.success()) {
                    render(response.data());
                } else {
                    errorLabel.setText(response != null ? response.message() : "网络错误");
                    statusLabel.setText("加载失败");
                }
            });
        });
    }

    private void render(MajorTransferWorkspace ws) {
        this.workspace = ws;
        boolean eligible = ws.activeBatch() != null && ws.eligibilityItems().stream()
                .allMatch(MajorTransferEligibilityItem::passed);
        statusLabel.setText(ws.activeBatch() != null ? "批次: " + ws.activeBatch().batchName() : "暂无开放批次");
        infoPanel.removeAll();
        infoPanel.add(new JLabel("学院：" + Objects.toString(ws.currentDepartmentName(), "未填写")));
        infoPanel.add(new JLabel("专业：" + Objects.toString(ws.currentMajorName(), "未填写")));
        infoPanel.add(new JLabel("班级：" + Objects.toString(ws.currentClassName(), "未填写")));
        infoPanel.add(new JLabel("学号：" + Objects.toString(ws.currentStudentNumber(), "未填写")));
        infoPanel.revalidate(); infoPanel.repaint();

        // Eligibility
        eligibilityPanel.removeAll();
        for (var item : ws.eligibilityItems()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            row.setOpaque(false);
            JLabel icon = new JLabel(item.passed() ? "[OK]" : "[NO]");
            icon.setForeground(item.passed() ? ACTION_GREEN : ACTION_RED);
            JLabel lbl = new JLabel(item.label() + ": " + item.detail());
            row.add(icon);
            row.add(lbl);
            eligibilityPanel.add(row);
        }
        eligibilityPanel.revalidate();
        eligibilityPanel.repaint();

        // Options
        targetMajorCombo.removeAllItems();
        for (var opt : ws.availableOptions()) {
            targetMajorCombo.addItem(new OptionItem(opt));
        }

        // Application state
        MajorTransferApplicationView app = ws.application();
        boolean isDraft = app != null && app.status() == MajorTransferStatus.DRAFT;
        boolean isSubmitted = app != null && app.status() == MajorTransferStatus.SUBMITTED;
        boolean canEdit = eligible && (app == null || isDraft);
        saveButton.setEnabled(canEdit);
        submitButton.setEnabled(isDraft && app.reason() != null && !app.reason().isBlank());
        withdrawButton.setVisible(isSubmitted);
        uploadButton.setEnabled(isDraft);

        if (app != null) {
            currentApplicationId = app.applicationId();
            for (int i = 0; i < targetMajorCombo.getItemCount(); i++) {
                if (targetMajorCombo.getItemAt(i).option.optionId().equals(app.optionId())) targetMajorCombo.setSelectedIndex(i);
            }
            applicationTypeCombo.setSelectedIndex(app.applicationType() == MajorTransferApplicationType.DIFFICULTY ? 1 : 0);
            reasonArea.setText(app.reason() != null ? app.reason() : "");
            reasonArea.setEditable(isDraft);
            targetMajorCombo.setEnabled(isDraft);
            applicationTypeCombo.setEnabled(isDraft);
            renderAttachments(app.attachments());
            renderTimeline(app);
        } else {
            currentApplicationId = null;
            reasonArea.setText("");
            reasonArea.setEditable(canEdit);
            targetMajorCombo.setEnabled(canEdit);
            applicationTypeCombo.setEnabled(canEdit);
            attachmentsPanel.removeAll();
            timelinePanel.removeAll();
            attachmentsPanel.revalidate();
            timelinePanel.revalidate();
        }
        setFormEnabled(true);
    }

    private void renderAttachments(List<MajorTransferApplicationView.AttachmentInfo> attachments) {
        attachmentsPanel.removeAll();
        for (var att : attachments) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            row.setOpaque(false);
            row.add(new JLabel(att.fileName() + " (" + (att.fileSize() / 1024) + " KB)"));
            if (workspace.application() != null
                    && workspace.application().status() == MajorTransferStatus.DRAFT) {
                JButton del = new JButton("删除");
                del.addActionListener(e -> deleteAttachment(att.attachmentId()));
                row.add(del);
            }
            attachmentsPanel.add(row);
        }
        attachmentsPanel.revalidate();
        attachmentsPanel.repaint();
    }

    private void renderTimeline(MajorTransferApplicationView app) {
        timelinePanel.removeAll();
        addTimelineItem(timelinePanel, "创建", app.createdAt(), statusLabel(app.status()));
        if (app.submittedAt() != null) {
            addTimelineItem(timelinePanel, "提交", app.submittedAt(), "已提交");
        }
        for (var review : app.reviews()) {
            addTimelineItem(timelinePanel, review.reviewStage().name(), review.createdAt(),
                    review.decision().name() + (review.comment() != null ? ": " + review.comment() : ""));
        }
        timelinePanel.revalidate();
        timelinePanel.repaint();
    }

    private void addTimelineItem(JPanel parent, String stage, java.time.Instant time, String detail) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row.setOpaque(false);
        String timeStr = time != null ? time.atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime().toString().substring(0, 16) : "";
        JLabel timeLabel = new JLabel(timeStr);
        timeLabel.setFont(UiTypography.CAPTION);
        timeLabel.setForeground(UiColors.TEXT_SECONDARY);
        JLabel stageLabel = new JLabel(stage);
        stageLabel.setFont(UiTypography.BODY);
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(UiTypography.CAPTION);
        row.add(timeLabel);
        row.add(stageLabel);
        row.add(detailLabel);
        parent.add(row);
    }

    private String statusLabel(MajorTransferStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case SUBMITTED -> "已提交";
            case SOURCE_APPROVED -> "原学院审核通过";
            case QUALIFIED -> "转入学院审核通过";
            case ASSESSED -> "已考核";
            case PROPOSED -> "拟录取";
            case PENDING_EFFECTIVE -> "待生效";
            case EFFECTIVE -> "已生效";
            case REJECTED -> "已驳回";
            case CANCELLED -> "已取消";
            case EXECUTION_FAILED -> "执行失败";
        };
    }

    private void saveDraft() {
        OptionItem selected = (OptionItem) targetMajorCombo.getSelectedItem();
        if (selected == null) {
            errorLabel.setText("请选择目标专业");
            return;
        }
        MajorTransferApplicationType type = applicationTypeCombo.getSelectedIndex() == 0
                ? MajorTransferApplicationType.ORDINARY : MajorTransferApplicationType.DIFFICULTY;
        long version = workspace != null && workspace.application() != null
                ? workspace.application().applicationVersion() : 0;
        var cmd = new SaveMajorTransferDraftCommand(currentApplicationId,
                selected.option.batchId(), selected.option.optionId(),
                type, reasonArea.getText(), version);
        setFormEnabled(false);
        errorLabel.setText(" ");
        students.saveTransferDraft(cmd).whenComplete((response, error) ->
                SwingUtilities.invokeLater(() -> {
                    setFormEnabled(true);
                    if (response != null && response.success()) {
                        errorLabel.setText(" ");
                        refresh();
                    }
                    else errorLabel.setText(response != null ? response.message() : "网络错误");
                }));
    }

    private void submit() {
        if (workspace == null || workspace.application() == null) return;
        String reason = reasonArea.getText();
        if (reason == null || reason.isBlank()) {
            errorLabel.setText("请填写申请理由后再提交");
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "确认提交转专业申请？提交后将等待管理员审核。", "确认",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        long version = workspace.application().applicationVersion();
        setFormEnabled(false);
        errorLabel.setText("正在提交...");
        students.submitTransfer(new SubmitMajorTransferCommand(
                workspace.application().applicationId(), version))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            setFormEnabled(true);
                            if (response != null && response.success()) {
                                errorLabel.setText(" ");
                                refresh();
                            }
                            else errorLabel.setText(response != null ? response.message() : "提交失败");
                        }));
    }

    private void withdraw() {
        if (workspace == null || workspace.application() == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "确认撤回转专业申请？", "确认",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        long version = workspace.application().applicationVersion();
        setFormEnabled(false);
        students.withdrawTransfer(new WithdrawMajorTransferCommand(
                workspace.application().applicationId(), version))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            setFormEnabled(true);
                            if (response != null && response.success()) refresh();
                            else errorLabel.setText(response != null ? response.message() : "撤回失败");
                        }));
    }

    private void uploadAttachment() {
        if (workspace == null || workspace.application() == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PDF/JPEG/PNG文件", "pdf", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path file = chooser.getSelectedFile().toPath();
        try {
            byte[] content = Files.readAllBytes(file);
            if (content.length > 5 * 1024 * 1024) {
                errorLabel.setText("附件大小不能超过5MB");
                return;
            }
            String name = file.getFileName().toString();
            String ct = name.endsWith(".pdf") ? "application/pdf" :
                    name.endsWith(".png") ? "image/png" : "image/jpeg";
            long version = workspace.application().applicationVersion();
            students.uploadTransferAttachment(new UploadMajorTransferAttachmentCommand(
                    workspace.application().applicationId(), name, ct, content, version))
                    .whenComplete((response, error) ->
                            SwingUtilities.invokeLater(() -> {
                                if (response != null && response.success()) refresh();
                                else errorLabel.setText(response != null ? response.message() : "上传失败");
                            }));
        } catch (IOException e) {
            errorLabel.setText("读取文件失败: " + e.getMessage());
        }
    }

    private void deleteAttachment(String attachmentId) {
        if (workspace == null || workspace.application() == null) return;
        long version = workspace.application().applicationVersion();
        students.deleteTransferAttachment(new DeleteMajorTransferAttachmentCommand(
                attachmentId, workspace.application().applicationId(), version))
                .whenComplete((response, error) ->
                        SwingUtilities.invokeLater(() -> {
                            if (response != null && response.success()) refresh();
                            else errorLabel.setText(response != null ? response.message() : "删除失败");
                        }));
    }

    private void setFormEnabled(boolean enabled) {
        busy = !enabled;
        boolean connected = connection.state() == ConnectionState.CONNECTED;
        boolean eligible = workspace != null && workspace.activeBatch() != null
                && workspace.eligibilityItems().stream().allMatch(MajorTransferEligibilityItem::passed);
        var app = workspace == null ? null : workspace.application();
        boolean editable = enabled && connected && eligible && (app == null || app.status() == MajorTransferStatus.DRAFT);
        saveButton.setEnabled(editable);
        uploadButton.setEnabled(editable && app != null);
        withdrawButton.setEnabled(enabled && connected && app != null && app.status() == MajorTransferStatus.SUBMITTED);
        reasonArea.setEditable(editable);
        targetMajorCombo.setEnabled(editable);
        applicationTypeCombo.setEnabled(editable);
        updateSubmitState();
    }

    private void updateSubmitState() {
        if (submitButton == null || reasonArea == null) return;
        boolean isDraft = workspace != null && workspace.application() != null
                && workspace.application().status() == MajorTransferStatus.DRAFT;
        String reason = reasonArea.getText();
        var app = isDraft ? workspace.application() : null;
        OptionItem selected = (OptionItem) targetMajorCombo.getSelectedItem();
        boolean saved = app != null && Objects.equals(reason, app.reason()) && selected != null
                && selected.option.optionId().equals(app.optionId())
                && applicationTypeCombo.getSelectedIndex() == (app.applicationType() == MajorTransferApplicationType.DIFFICULTY ? 1 : 0);
        submitButton.setEnabled(!busy && connection.state() == ConnectionState.CONNECTED && isDraft && saved
                && workspace.eligibilityItems().stream().allMatch(MajorTransferEligibilityItem::passed)
                && reason != null && !reason.isBlank());
    }

    private void connectionChanged(ConnectionState state) {
        if (!active) return;
        SwingUtilities.invokeLater(() -> {
            boolean connected = state == ConnectionState.CONNECTED;
            refreshButton.setEnabled(connected);
            setFormEnabled(!busy);
            if (!connected) {
                statusLabel.setText("连接断开");
            }
        });
    }

    private static JLabel label(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UiTypography.BODY);
        return lbl;
    }

    private static JLabel sectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UiTypography.SECTION_TITLE);
        lbl.setForeground(UiColors.TEXT_PRIMARY);
        lbl.setBorder(new EmptyBorder(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_1, 0));
        return lbl;
    }

    /** Option combo item wrapping a transfer option view. */
    private record OptionItem(MajorTransferOptionView option) {
        @Override public String toString() {
            return option.targetDepartmentName() + " / " + option.targetMajorName()
                    + " (名额:" + option.receiveQuota() + ")";
        }
    }
}
