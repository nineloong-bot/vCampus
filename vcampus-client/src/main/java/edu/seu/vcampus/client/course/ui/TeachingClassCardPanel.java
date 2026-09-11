package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.TeachingClassOptionView;

import javax.swing.*;
import java.awt.*;

/** White teaching-class card displayed inside an expanded course row. */
final class TeachingClassCardPanel extends JPanel {
    TeachingClassCardPanel(TeachingClassOptionView option, JButton action) {
        OfferingSummary offering = option.offering();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        setPreferredSize(new Dimension(310, 210));
        setMaximumSize(new Dimension(340, 230));
        JLabel title = line("[" + offering.className() + "]  教师 " + offering.teacherUserId());
        title.setFont(UiTypography.BODY_BOLD);
        add(title);
        add(Box.createVerticalStrut(UiSpacing.SM));
        if (option.actionType().equals("RETAKE")) add(badge("重修专用名额"));
        add(line(StudentCourseSelectionPanel.scheduleText(offering)));
        add(Box.createVerticalStrut(UiSpacing.SM));
        int capacity = option.actionType().equals("RETAKE")
                ? offering.retakeCapacity() : offering.capacity();
        int enrolled = option.actionType().equals("RETAKE")
                ? offering.retakeEnrolledCount() : offering.enrolledCount();
        add(line("课容量：" + capacity + " 人"));
        add(line("已选人数：" + enrolled));
        if (option.actionReason() != null) add(line(option.actionReason()));
        add(Box.createVerticalGlue());
        action.setAlignmentX(LEFT_ALIGNMENT);
        add(action);
    }

    private static JLabel line(String text) {
        JLabel label = new JLabel("<html>" + text.replace("；", "<br>") + "</html>");
        label.setFont(UiTypography.BODY);
        label.setForeground(UiColors.TEXT_PRIMARY);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel badge(String text) {
        JLabel label = line(text);
        label.setForeground(new Color(70, 150, 45));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(190, 220, 170)),
                BorderFactory.createEmptyBorder(3, 8, 3, 8)));
        return label;
    }
}
