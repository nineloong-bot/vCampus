package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CourseEditorPanelTest {

    @Test
    void curriculumFieldLabelsStayLeftAlignedWhileTyping() throws Exception {
        CourseUiGateway gateway = new CourseUiGateway() {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(
                    OfferingSearchQuery query) {
                return CourseUiGateway.preview().searchOfferings(query);
            }
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        CourseEditorPanel editor = onEdt(() -> new CourseEditorPanel(gateway, null,
                () -> { }, () -> { }));

        JLabel courseName = findLabel(editor.component(), "课程名称");
        JTextField courseCode = findField(editor.component(), "课程代码");
        onEdt(() -> {
            editor.component().setSize(700, 700);
            layout(editor.component());
            return null;
        });

        assertThat(courseName.getAlignmentX()).isEqualTo(Component.LEFT_ALIGNMENT);
        assertThat(SwingUtilities.convertPoint(courseCode.getParent(), courseCode.getLocation(),
                editor.component()).x).isLessThan(100);
    }
    private static JLabel findLabel(Container root, String text) { return descendants(root).stream()
            .filter(JLabel.class::isInstance).map(JLabel.class::cast)
            .filter(value -> text.equals(value.getText())).findFirst().orElseThrow(); }
    private static JTextField findField(Container root, String name) { return descendants(root).stream()
            .filter(JTextField.class::isInstance).map(JTextField.class::cast)
            .filter(value -> name.equals(value.getAccessibleContext().getAccessibleName()))
            .findFirst().orElseThrow(); }
    private static void layout(Container root) { root.doLayout(); for (Component child : root.getComponents())
            if (child instanceof Container nested) layout(nested); }
    private static java.util.List<Component> descendants(Container root) { java.util.List<Component> all = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) { all.add(child); if (child instanceof Container nested) all.addAll(descendants(nested)); } return all; }
    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception { AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Exception failure) { throw new RuntimeException(failure); } }); return result.get(); }
}
