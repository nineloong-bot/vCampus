package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.OfferingSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.Window;

/** Form layout and existing-offering prefill for the offering editor segments. */
abstract class OfferingEditorDialogForm extends OfferingEditorDialogLoading {

    OfferingEditorDialogForm(Window owner, CourseUiGateway gateway, OfferingSummary existing,
            Runnable onSaved) {
        super(owner, gateway, existing, onSaved);
    }

    JPanel title() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = label(existing == null ? "新建教学班" : "编辑教学班", UiColors.TEXT_PRIMARY);
        heading.setFont(UiTypography.PAGE_TITLE);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label("从学期、课程和在职教师中选择，并逐行维护上课安排", UiColors.TEXT_SECONDARY));
        return panel;
    }

    JScrollPane form() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(referenceLine());
        panel.add(pair("学期（必填）", term, "课程（必填）", course));
        panel.add(searchLine("课程关键字", courseKeyword, "查询课程"));
        panel.add(pair("教师（必填）", teacher, "教学班名称（必填）", className));
        panel.add(searchLine("教师关键字", teacherKeyword, "查询教师"));
        status.setFont(UiTypography.BODY);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        status.getAccessibleContext().setAccessibleName("教学班状态");
        panel.add(pair("普通选课容量（必填）", capacity, "重修专用容量", retakeCapacity));
        panel.add(pair("教学班状态", status, "容量说明",
                label("普通与重修分别计数，共用教师和时间", UiColors.TEXT_SECONDARY)));
        panel.add(label("上课安排（必填）", UiColors.TEXT_PRIMARY));
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(schedules);
        JScrollPane scroll = new JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(error);
        panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> dispose());
        panel.add(cancel);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(save);
        return panel;
    }

    void fill(OfferingSummary value) {
        className.setText(value.className());
        capacity.setValue(value.capacity());
        retakeCapacity.setValue(value.retakeCapacity());
        status.setSelectedItem(StatusChoice.fromCode(value.offeringStatus()));
        schedules.setSchedules(value.schedules());
    }

    private JPanel referenceLine() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(referenceStatus);
        panel.add(Box.createHorizontalGlue());
        panel.add(retry);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return panel;
    }

    private JPanel searchLine(String caption, JTextField keyword, String actionText) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.add(label(caption, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(keyword);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton action = AbstractCoursePanel.secondary(actionText);
        action.addActionListener(event -> loadReferences());
        panel.add(action);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        return panel;
    }

    private JPanel pair(String leftLabel, java.awt.Component left, String rightLabel, java.awt.Component right) {
        JPanel pair = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.LG, 0));
        pair.setOpaque(false);
        pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        pair.add(row(leftLabel, left));
        pair.add(row(rightLabel, right));
        return pair;
    }

    private JPanel row(String text, java.awt.Component input) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        JLabel caption = label(text, UiColors.TEXT_PRIMARY);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        row.add(caption);
        row.add(Box.createVerticalStrut(UiSpacing.SM));
        if (input instanceof javax.swing.JComponent component) component.setAlignmentX(LEFT_ALIGNMENT);
        row.add(input);
        row.add(Box.createVerticalStrut(UiSpacing.MD));
        return row;
    }
}
