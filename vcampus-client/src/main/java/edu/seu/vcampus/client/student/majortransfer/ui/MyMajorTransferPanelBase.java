package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationType;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferEligibilityItem;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferWorkspace;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Shared state, editability rules and small helpers for the major-transfer segments. */
abstract class MyMajorTransferPanelBase extends JPanel {
    static final Color TABLE_BORDER = new Color(178, 218, 211);
    static final Color TABLE_LABEL = new Color(239, 247, 245);
    static final Color ACTION_GREEN = new Color(139, 195, 74);
    static final Color ACTION_RED = new Color(244, 67, 54);

    protected final StudentClientService students;
    protected final ClientConnection connection;
    protected final AtomicLong generation = new AtomicLong();

    protected JLabel statusLabel, errorLabel;
    protected JButton refreshButton, saveButton, submitButton, withdrawButton, uploadButton;
    protected JPanel eligibilityPanel, applicationPanel, timelinePanel;
    protected MajorTransferFlowChartPanel flowChartPanel;
    protected JPanel infoPanel;
    protected boolean busy;
    protected JComboBox<MajorTransferOptionItem> targetMajorCombo;
    protected JComboBox<String> applicationTypeCombo;
    protected JTextArea reasonArea;
    protected JPanel attachmentsPanel;
    protected volatile boolean active;
    protected MajorTransferWorkspace workspace;
    protected String currentApplicationId;

    /** Stores the service and connection shared by the panel segments. */
    protected MyMajorTransferPanelBase(StudentClientService students, ClientConnection connection) {
        this.students = Objects.requireNonNull(students);
        this.connection = Objects.requireNonNull(connection);
    }

    /** Removes one attachment; implemented by the segment that owns the action flows. */
    abstract void deleteAttachment(String attachmentId);

    void setFormEnabled(boolean enabled) {
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

    void updateSubmitState() {
        if (submitButton == null || reasonArea == null) return;
        boolean isDraft = workspace != null && workspace.application() != null
                && workspace.application().status() == MajorTransferStatus.DRAFT;
        String reason = reasonArea.getText();
        var app = isDraft ? workspace.application() : null;
        MajorTransferOptionItem selected = (MajorTransferOptionItem) targetMajorCombo.getSelectedItem();
        boolean saved = app != null && Objects.equals(reason, app.reason()) && selected != null
                && selected.option().optionId().equals(app.optionId())
                && applicationTypeCombo.getSelectedIndex() == (app.applicationType() == MajorTransferApplicationType.DIFFICULTY ? 1 : 0);
        submitButton.setEnabled(!busy && connection.state() == ConnectionState.CONNECTED && isDraft && saved
                && workspace.eligibilityItems().stream().allMatch(MajorTransferEligibilityItem::passed)
                && reason != null && !reason.isBlank());
    }

    static String statusLabel(MajorTransferStatus status) {
        return switch (status) {
            case DRAFT -> "草稿";
            case SUBMITTED -> "已提交";
            case SOURCE_APPROVED -> "原学院审核通过";
            case QUALIFIED -> "转入学院审核通过";
            case ASSESSED -> "已考核，待终审";
            case PENDING_EFFECTIVE -> "待生效";
            case EFFECTIVE -> "已生效";
            case REJECTED -> "已驳回";
            case CANCELLED -> "已取消";
            case EXECUTION_FAILED -> "执行失败";
        };
    }

    static JLabel label(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UiTypography.BODY);
        return lbl;
    }

    static JLabel sectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(UiTypography.SECTION_TITLE);
        lbl.setForeground(UiColors.TEXT_PRIMARY);
        lbl.setBorder(new EmptyBorder(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_1, 0));
        return lbl;
    }
}
