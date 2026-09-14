package edu.seu.vcampus.client.course.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class StudentCourseSelectionPresentationTest {
    @Test
    void omitsRedundantDescription() throws Exception {
        StudentCourseSelectionPanel panel = selectionPanel();

        assertThat(labels(panel)).doesNotContain(
                "按课程查看可选教学班；选课、退课和重修会根据当前阶段与学生状态自动开放。");
    }

    @Test
    void alignsHeaderAndRowColumns() throws Exception {
        StudentCourseSelectionPanel panel = selectionPanel();
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(1600, 900);
            layoutTree(panel);
        });

        JPanel header = named(panel, "可选课程表头", JPanel.class);
        StudentCourseRowPanel firstRow = descendants(panel).stream()
                .filter(StudentCourseRowPanel.class::isInstance)
                .map(StudentCourseRowPanel.class::cast).findFirst().orElseThrow();
        JPanel row = Arrays.stream(firstRow.getComponents())
                .filter(JPanel.class::isInstance).map(JPanel.class::cast)
                .filter(candidate -> Arrays.stream(candidate.getComponents())
                        .filter(JLabel.class::isInstance).count() == 7)
                .findFirst().orElseThrow();
        List<JLabel> headerCells = Arrays.stream(header.getComponents()).map(JLabel.class::cast).toList();
        List<JLabel> rowCells = Arrays.stream(row.getComponents()).map(JLabel.class::cast).toList();

        assertThat(rowCells).hasSameSizeAs(headerCells);
        for (int column = 0; column < headerCells.size(); column++) {
            Rectangle expected = SwingUtilities.convertRectangle(header, headerCells.get(column).getBounds(), panel);
            Rectangle actual = SwingUtilities.convertRectangle(row, rowCells.get(column).getBounds(), panel);
            assertThat(actual.x).as("column %s x", column).isEqualTo(expected.x);
            assertThat(actual.width).as("column %s width", column).isEqualTo(expected.width);
        }
    }

    private static StudentCourseSelectionPanel selectionPanel() throws Exception {
        StudentCourseSelectionPanel panel = onEdt(
                () -> new StudentCourseSelectionPanel(CourseUiGateway.preview()));
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
        return panel;
    }

    private static List<String> labels(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance)
                .map(JLabel.class::cast).map(JLabel::getText).toList();
    }

    private static <T extends Component> T named(Container root, String name, Class<T> type) {
        return descendants(root).stream().filter(type::isInstance)
                .map(type::cast).filter(component -> name.equals(
                        component.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static List<Component> descendants(Container root) {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) layoutTree(container);
        }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
