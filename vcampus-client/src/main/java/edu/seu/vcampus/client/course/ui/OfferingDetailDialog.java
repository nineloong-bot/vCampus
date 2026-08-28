package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;

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
    private static JPanel summary(String heading, String value, String note) {
        JPanel p = new JPanel(); p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        p.setBackground(UiColors.BACKGROUND_SUBTLE); p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT), BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        JLabel h = new JLabel(heading); h.setFont(UiTypography.SECTION_TITLE); p.add(h);
        p.add(javax.swing.Box.createVerticalStrut(UiSpacing.MD)); JLabel v = new JLabel(value); v.setFont(UiTypography.BODY_BOLD); p.add(v);
        p.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM)); JLabel n = new JLabel(note); n.setFont(UiTypography.CAPTION); n.setForeground(UiColors.TEXT_SECONDARY); p.add(n); return p;
    }
}
