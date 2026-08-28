package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.stream.Collectors;

/** Standard 720 px offering detail/change-preview dialog. */
public final class OfferingDetailDialog extends JDialog {
    public OfferingDetailDialog(Window owner, String courseName, String sourceClass, String targetClass) {
        super(owner, "教学班详情", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.XL));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        JLabel title = new JLabel(courseName + " · 改选确认"); title.setFont(UiTypography.PAGE_TITLE); title.setForeground(UiColors.TEXT_PRIMARY);
        root.add(title, BorderLayout.NORTH);
        JPanel comparison = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.XL, 0));
        comparison.setOpaque(false);
        comparison.add(summary("原教学班", sourceClass, "改选成功后原记录失效"));
        comparison.add(summary("目标教学班", targetClass, "提交前将再次校验容量与冲突"));
        root.add(comparison, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.MD, 0)); actions.setOpaque(false);
        var cancel = AbstractCoursePanel.secondary("取消改选"); cancel.addActionListener(e -> dispose()); actions.add(cancel);
        actions.add(AbstractCoursePanel.primary("确认改选")); root.add(actions, BorderLayout.SOUTH);
        setContentPane(root); setSize(720, 420); setLocationRelativeTo(owner);
    }

    public OfferingDetailDialog(Window owner, OfferingSummary source, OfferingSummary target,
                                String conflictResult, Runnable onConfirm) {
        super(owner, "改选确认", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.XL));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        JLabel title = new JLabel("改选教学班确认");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        root.add(title, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.LG));
        content.setOpaque(false);
        JPanel comparison = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.XL, 0));
        comparison.setOpaque(false);
        comparison.add(offeringSummary("原教学班", source, "确认后仅在目标班校验成功时退出"));
        comparison.add(offeringSummary("目标教学班", target, "服务端将再次校验容量、重复和冲突"));
        content.add(comparison, BorderLayout.CENTER);
        JLabel conflict = new JLabel(conflictResult);
        conflict.setFont(UiTypography.BODY_BOLD);
        conflict.setForeground(conflictResult.startsWith("未发现") ? UiColors.SUCCESS_FG : UiColors.ACCENT);
        conflict.setOpaque(true);
        conflict.setBackground(conflictResult.startsWith("未发现") ? UiColors.SUCCESS_BG : UiColors.BACKGROUND_SUBTLE);
        conflict.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG, UiSpacing.MD, UiSpacing.LG));
        content.add(conflict, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.MD, 0));
        actions.setOpaque(false);
        var cancel = AbstractCoursePanel.secondary("取消改选");
        cancel.addActionListener(event -> dispose());
        actions.add(cancel);
        var confirm = AbstractCoursePanel.primary("确认改选");
        confirm.addActionListener(event -> { onConfirm.run(); dispose(); });
        actions.add(confirm);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(720, 460);
        setLocationRelativeTo(owner);
    }

    private static JPanel offeringSummary(String heading, OfferingSummary offering, String note) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        panel.add(text(heading, UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.MD));
        panel.add(text(offering.courseName() + " · " + offering.className(), UiTypography.BODY_BOLD, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM));
        panel.add(text("课程代码：" + offering.courseCode(), UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM));
        panel.add(text("时间：" + scheduleText(offering), UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM));
        panel.add(text("容量：" + offering.enrolledCount() + " / " + offering.capacity(), UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.MD));
        panel.add(text(note, UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        return panel;
    }

    private static JLabel text(String value, java.awt.Font font, java.awt.Color color) {
        JLabel label = new JLabel(value);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static String scheduleText(OfferingSummary offering) {
        if (offering.schedules().isEmpty()) return "待安排";
        return offering.schedules().stream().map(OfferingDetailDialog::scheduleText).collect(Collectors.joining("；"));
    }

    private static String scheduleText(ScheduleItem item) {
        String day = switch (item.dayOfWeek()) {
            case "MONDAY" -> "周一"; case "TUESDAY" -> "周二"; case "WEDNESDAY" -> "周三";
            case "THURSDAY" -> "周四"; case "FRIDAY" -> "周五"; case "SATURDAY" -> "周六";
            case "SUNDAY" -> "周日"; default -> item.dayOfWeek();
        };
        return day + " 第" + item.startPeriod() + "–" + item.endPeriod() + "节 第"
                + item.startWeek() + "–" + item.endWeek() + "周 " + item.classroom();
    }
    private static JPanel summary(String heading, String value, String note) {
        JPanel p = new JPanel(); p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setBackground(UiColors.BACKGROUND_SUBTLE); p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT), BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        JLabel h = new JLabel(heading); h.setFont(UiTypography.SECTION_TITLE); p.add(h);
        p.add(javax.swing.Box.createVerticalStrut(UiSpacing.MD)); JLabel v = new JLabel(value); v.setFont(UiTypography.BODY_BOLD); p.add(v);
        p.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM)); JLabel n = new JLabel(note); n.setFont(UiTypography.CAPTION); n.setForeground(UiColors.TEXT_SECONDARY); p.add(n); return p;
    }
}
