package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import java.awt.*;

/** Assembles the major-transfer page and reacts to connection changes. */
abstract class MyMajorTransferPanelLayout extends MyMajorTransferPanelForm {

    /** Creates the layout segment of the major-transfer panel. */
    protected MyMajorTransferPanelLayout(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    void connectionChanged(ConnectionState state) {
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

    void build() {
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
        flowChartPanel = new MajorTransferFlowChartPanel();
        content.add(flowChartPanel);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_3));
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
}
