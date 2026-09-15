package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.*;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TermEditorPanelTest {
    @Test
    void updatePreservesIdentityWindowsAndOptimisticVersion() throws Exception {
        TermView existing = CourseUiGateway.preview().listTerms().join().getFirst();
        AtomicReference<UpdateTermCommand> submitted = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<TermView> updateTerm(UpdateTermCommand command) {
                submitted.set(command); return CompletableFuture.completedFuture(existing);
            }
        };
        TermEditorPanel editor = onEdt(() -> new TermEditorPanel(gateway, existing, () -> { }, () -> { }));
        editor.onOpened();

        SwingUtilities.invokeAndWait(() -> {
            field(editor.component(), "学期名称").setText("秋季学期（调整）");
            button(editor.component(), "保存修改").doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new UpdateTermCommand(existing.termId(), existing.termCode(),
                "秋季学期（调整）", existing.startDate(), existing.endDate(), existing.academicYearStart(),
                existing.season(), existing.enrollmentStartAt(), existing.enrollmentEndAt(),
                existing.adjustmentStartAt(), existing.adjustmentEndAt(), existing.termStatus(), existing.rowVersion()));
    }

    private static JTextField field(Container root, String name) { return component(root, name, JTextField.class); }
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
