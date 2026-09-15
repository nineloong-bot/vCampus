package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CourseEditorPanelTest {
    @Test
    void submitsTrimmedCreateCommandAndReportsSuccess() throws Exception {
        AtomicReference<CreateCourseCommand> submitted = new AtomicReference<>();
        AtomicReference<Boolean> saved = new AtomicReference<>(false);
        CourseUiGateway gateway = new CourseUiGateway() {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new CourseView("course-1", command.courseCode(),
                        command.courseName(), command.credit(), command.totalHours(), command.description(),
                        command.active(), 0, Instant.now(), Instant.now()));
            }
        };
        CourseEditorPanel editor = onEdt(() -> new CourseEditorPanel(gateway, null,
                () -> saved.set(true), () -> { }));
        editor.onOpened();

        SwingUtilities.invokeAndWait(() -> {
            field(editor.component(), "课程代码").setText(" SE101 ");
            field(editor.component(), "课程名称").setText(" 软件工程导论 ");
            spinner(editor.component(), "学分").setValue(new BigDecimal("4.5"));
            spinner(editor.component(), "总学时").setValue(72);
            button(editor.component(), "创建课程").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new CreateCourseCommand(
                "SE101", "软件工程导论", new BigDecimal("4.5"), 72, "", true));
        assertThat(saved.get()).isTrue();
    }

    private static JTextField field(Container root, String name) { return component(root, name, JTextField.class); }
    private static JSpinner spinner(Container root, String name) { return component(root, name, JSpinner.class); }
    private static JButton button(Container root, String text) { return descendants(root).stream().filter(JButton.class::isInstance)
            .map(JButton.class::cast).filter(value -> text.equals(value.getText())).findFirst().orElseThrow(); }
    private static <T extends Component> T component(Container root, String name, Class<T> type) { return descendants(root).stream()
            .filter(type::isInstance).map(type::cast).filter(value -> name.equals(value.getAccessibleContext().getAccessibleName()))
            .findFirst().orElseThrow(); }
    private static java.util.List<Component> descendants(Container root) { java.util.List<Component> all = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) { all.add(child); if (child instanceof Container nested) all.addAll(descendants(nested)); } return all; }
    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception { AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Exception failure) { throw new RuntimeException(failure); } }); return result.get(); }
}
