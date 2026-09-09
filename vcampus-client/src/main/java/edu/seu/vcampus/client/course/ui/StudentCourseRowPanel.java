package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.CourseSelectionView;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Table-like course row with a collapsible teacher-card surface. */
final class StudentCourseRowPanel extends JPanel {
    private final JPanel expansion;
    private final JButton toggle = new JButton("›");

    StudentCourseRowPanel(CourseSelectionView course, JButton action, JPanel expansion,
                          Consumer<StudentCourseRowPanel> onExpand) {
        super(new BorderLayout());
        this.expansion = expansion;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_DEFAULT));
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.MD, UiSpacing.MD, UiSpacing.MD));
        addCell(row, course.courseCode(), 0, 1.05);
        addCell(row, course.courseName(), 1, 1.45);
        addCell(row, course.teachingClasses().size() + " 个", 2, .75);
        addCell(row, nature(course.courseNature()) + (course.retakeCourse() ? " · 重修" : ""), 3, .9);
        addCell(row, course.offeringUnit(), 4, 1.45);
        addCell(row, course.credit().stripTrailingZeros().toPlainString(), 5, .55);
        GridBagConstraints actionCell = constraints(6, .8);
        row.add(action, actionCell);
        toggle.setFont(UiTypography.PAGE_TITLE);
        toggle.setBorderPainted(false); toggle.setContentAreaFilled(false);
        toggle.getAccessibleContext().setAccessibleName("展开课程 " + course.courseCode());
        toggle.addActionListener(event -> { if (!isExpanded()) onExpand.accept(this); else collapse(); });
        row.add(toggle, constraints(7, .25));
        add(row, BorderLayout.NORTH);
        expansion.setVisible(false);
        add(expansion, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
    }

    void expand() {
        expansion.setVisible(true); toggle.setText("⌄");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 330)); revalidate();
    }

    void collapse() {
        expansion.setVisible(false); toggle.setText("›");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 72)); revalidate();
    }

    boolean isExpanded() { return expansion.isVisible(); }

    private static void addCell(JPanel row, String text, int column, double weight) {
        JLabel label = new JLabel(text);
        label.setFont(column == 0 || column == 1 ? UiTypography.BODY_BOLD : UiTypography.BODY);
        label.setForeground(UiColors.TEXT_PRIMARY);
        row.add(label, constraints(column, weight));
    }

    private static GridBagConstraints constraints(int column, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column; c.weightx = weight; c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 6, 0, 6);
        return c;
    }

    private static String nature(String value) {
        return switch (value) { case "REQUIRED" -> "必修"; case "RESTRICTED" -> "限选"; default -> "任选"; };
    }
}
