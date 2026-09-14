package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;

final class MajorTransferFlowHeader extends JPanel {
    private final JLabel titleLabel = new JLabel("转专业申请流程");
    private final JLabel statusBadge = new JLabel(" 未开始 ");
    private final JLabel departmentLabel = new JLabel("申请院系: --");
    private final JLabel timeLabel = new JLabel("上次更新时间: --");

    MajorTransferFlowHeader() {
        super(new BorderLayout(12, 6));
        setOpaque(false);
        var leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBox.setOpaque(false);
        leftBox.add(stepNumber());
        leftBox.add(titleAndMetadata());
        add(leftBox, BorderLayout.WEST);
        setBadge("未开始", MajorTransferFlowChartPanel.StepStatus.PENDING);
    }

    void setTitle(String title) {
        titleLabel.setText(title);
    }

    void setMetadata(String department, String updateTime) {
        departmentLabel.setText("申请院系: " + department);
        timeLabel.setText("上次更新时间: " + updateTime);
    }

    void setBadge(String text, MajorTransferFlowChartPanel.StepStatus status) {
        statusBadge.setText(" " + text + " ");
        statusBadge.setBackground(status.bg);
        statusBadge.setForeground(status.color);
        statusBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(status.color, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
    }

    private static JLabel stepNumber() {
        var label = new JLabel("1");
        label.setFont(new Font("SansSerif", Font.BOLD, 32));
        label.setForeground(new Color(180, 205, 237));
        return label;
    }

    private JPanel titleAndMetadata() {
        var box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(titleRow());
        box.add(Box.createVerticalStrut(4));
        box.add(metadataRow());
        return box;
    }

    private JPanel titleRow() {
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLabel.setForeground(UiColors.TEXT_PRIMARY);
        row.add(titleLabel);
        statusBadge.setFont(UiTypography.CAPTION.deriveFont(Font.BOLD));
        statusBadge.setOpaque(true);
        row.add(statusBadge);
        return row;
    }

    private JPanel metadataRow() {
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        row.setOpaque(false);
        for (var label : new JLabel[]{departmentLabel, timeLabel}) {
            label.setFont(UiTypography.CAPTION);
            label.setForeground(UiColors.TEXT_SECONDARY);
            row.add(label);
        }
        return row;
    }
}
