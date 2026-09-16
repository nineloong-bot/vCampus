package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.CourseSelectionView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Table-like course row whose full header toggles its teacher-card surface. */
final class StudentCourseRowPanel extends JPanel {
    private final JPanel expansion;
    private final JLabel indicator = new JLabel("›");
    private final Consumer<StudentCourseRowPanel> onExpand;

    StudentCourseRowPanel(CourseSelectionView course, JPanel expansion,
                          Consumer<StudentCourseRowPanel> onExpand) {
        super(new BorderLayout());
        this.expansion = expansion;
        this.onExpand = onExpand;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.BORDER_DEFAULT));
        setFocusable(true);
        getAccessibleContext().setAccessibleName("展开课程 " + course.courseCode());

        JPanel row = new JPanel(new GridLayout(1, 7));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(
                UiSpacing.MD, UiSpacing.MD, UiSpacing.MD, UiSpacing.MD));
        addCell(row, course.courseCode(), 0);
        addCell(row, course.courseName(), 1);
        addCell(row, course.teachingClasses().size() + " 个", 2);
        addCell(row, nature(course.courseNature())
                + (course.retakeCourse() ? " · 重修" : ""), 3);
        addCell(row, course.offeringUnit(), 4);
        addCell(row, course.credit().stripTrailingZeros().toPlainString(), 5);
        indicator.setFont(UiTypography.PAGE_TITLE);
        indicator.setHorizontalAlignment(SwingConstants.CENTER);
        row.add(indicator);
        installMouseToggle(row);
        add(row, BorderLayout.NORTH);

        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "toggle");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "toggle");
        getActionMap().put("toggle", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                toggle();
            }
        });

        expansion.setVisible(false);
        add(expansion, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
    }

    void expand() {
        expansion.setVisible(true);
        indicator.setText("⌄");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));
        revalidate();
    }

    void collapse() {
        expansion.setVisible(false);
        indicator.setText("›");
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        revalidate();
    }

    boolean isExpanded() {
        return expansion.isVisible();
    }

    private void toggle() {
        if (isExpanded()) collapse();
        else onExpand.accept(this);
    }

    private void installMouseToggle(Component component) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                requestFocusInWindow();
                toggle();
            }
        });
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) installMouseToggle(child);
        }
    }

    private static void addCell(JPanel row, String text, int column) {
        JLabel label = new JLabel(text);
        label.setFont(column == 0 || column == 1 ? UiTypography.BODY_BOLD : UiTypography.BODY);
        label.setForeground(UiColors.TEXT_PRIMARY);
        row.add(label);
    }

    private static String nature(String value) {
        return switch (value) {
            case "REQUIRED" -> "必修";
            case "CROSS_DISCIPLINARY" -> "跨学科";
            default -> "选修";
        };
    }
}
