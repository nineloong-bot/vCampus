package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.util.function.IntConsumer;

/** Reusable course-module page controls until the shared PagedTable component is published. */
final class CoursePager extends JPanel {
    private final int pageSize;
    private final IntConsumer navigation;
    private final JButton previous = AbstractCoursePanel.secondary("上一页");
    private final JButton next = AbstractCoursePanel.secondary("下一页");
    private final JLabel position = AbstractCoursePanel.label(
            "第 1 / 1 页", UiTypography.BODY, UiColors.TEXT_PRIMARY);
    private int currentPage;
    private int totalPages = 1;

    CoursePager(int pageSize, IntConsumer navigation) {
        this.pageSize = pageSize;
        this.navigation = navigation;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        add(AbstractCoursePanel.label("每页 " + pageSize + " 条", UiTypography.CAPTION,
                UiColors.TEXT_SECONDARY));
        add(Box.createHorizontalGlue());
        previous.addActionListener(event -> navigateTo(currentPage - 1));
        next.addActionListener(event -> navigateTo(currentPage + 1));
        add(previous);
        add(Box.createHorizontalStrut(UiSpacing.SM));
        add(position);
        add(Box.createHorizontalStrut(UiSpacing.SM));
        add(next);
        updateControls();
    }

    void showPage(int page, long total) {
        totalPages = Math.max(1, (int) ((total + pageSize - 1) / pageSize));
        currentPage = Math.max(0, Math.min(page, totalPages - 1));
        position.setText("第 " + (currentPage + 1) + " / " + totalPages + " 页");
        updateControls();
    }

    int currentPage() {
        return currentPage;
    }

    private void navigateTo(int page) {
        if (page < 0 || page >= totalPages || page == currentPage) return;
        navigation.accept(page);
    }

    private void updateControls() {
        previous.setEnabled(currentPage > 0);
        next.setEnabled(currentPage + 1 < totalPages);
    }
}
