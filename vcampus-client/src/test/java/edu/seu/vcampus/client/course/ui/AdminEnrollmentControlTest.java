package edu.seu.vcampus.client.course.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import edu.seu.vcampus.common.course.AdminEnrollStudentCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.OfferingSummary;

class AdminEnrollmentControlTest {
    @Test
    void submitsSelectedOfferingAndStudentNumberThenRefreshes() throws Exception {
        AtomicReference<AdminEnrollStudentCommand> submitted = new AtomicReference<>();
        AtomicInteger refreshes = new AtomicInteger();
        CourseUiGateway gateway = new CourseUiGateway() {
            @Override public CompletableFuture<EnrollmentView> adminEnrollStudent(
                    AdminEnrollStudentCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "e-1", command.offeringId(), "s-1", "RETAKE", "ACTIVE",
                        Instant.now(), null, 0));
            }
            @Override public CompletableFuture<edu.seu.vcampus.common.paging.PageResult<OfferingSummary>>
                    searchOfferings(edu.seu.vcampus.common.course.OfferingSearchQuery query) {
                throw new UnsupportedOperationException();
            }
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                throw new UnsupportedOperationException();
            }
            @Override public CompletableFuture<List<edu.seu.vcampus.common.course.ScheduleItem>> currentSchedule() {
                throw new UnsupportedOperationException();
            }
            @Override public CompletableFuture<EnrollmentView> enroll(
                    edu.seu.vcampus.common.course.EnrollCommand command) {
                throw new UnsupportedOperationException();
            }
        };
        OfferingSummary offering = new OfferingSummary("offering-1", "term-1", "course-1",
                "CS101", "程序设计", "teacher-1", "A班", 40, 20, 5, 5,
                "OPEN", 0, List.of());
        AdminEnrollmentControl control = new AdminEnrollmentControl(
                gateway, offering, refreshes::incrementAndGet, () -> { }, ignored -> { });
        control.activate();

        field(control).setText("213260001");
        SwingUtilities.invokeAndWait(() -> button(control, "确认添加").doClick());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(
                new AdminEnrollStudentCommand("213260001", "offering-1"));
        assertThat(refreshes).hasValue(1);
    }

    private static JTextField field(Container root) {
        return descendants(root, JTextField.class).getFirst();
    }

    private static JButton button(Container root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Container root, Class<T> type) {
        java.util.ArrayList<T> result = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) result.add(type.cast(child));
            if (child instanceof Container container) result.addAll(descendants(container, type));
        }
        return result;
    }
}
