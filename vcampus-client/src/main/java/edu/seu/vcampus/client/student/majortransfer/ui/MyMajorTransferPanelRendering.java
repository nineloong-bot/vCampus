package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationType;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferEligibilityItem;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferWorkspace;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Workspace rendering for the major-transfer panel. */
abstract class MyMajorTransferPanelRendering extends MyMajorTransferPanelBase {

    /** Creates the rendering segment of the major-transfer panel. */
    protected MyMajorTransferPanelRendering(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void render(MajorTransferWorkspace ws) {
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
            targetMajorCombo.addItem(new MajorTransferOptionItem(opt));
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
                if (targetMajorCombo.getItemAt(i).option().optionId().equals(app.optionId())) targetMajorCombo.setSelectedIndex(i);
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
        if (flowChartPanel != null) {
            flowChartPanel.update(app, ws.availableOptions());
        }
        setFormEnabled(true);
    }

    void renderAttachments(List<MajorTransferApplicationView.AttachmentInfo> attachments) {
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

    void renderTimeline(MajorTransferApplicationView app) {
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

    void addTimelineItem(JPanel parent, String stage, java.time.Instant time, String detail) {
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
}
