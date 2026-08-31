package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.EnrollmentChangeDialog;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentChangeDialogTest {
    @AfterEach
    void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void formRendersWithCurrentStudentInfo() throws Exception {
        var client = new RecordingStudentClient();
        EnrollmentChangeDialog dialog = dialog(client, profile());

        assertThat(label(dialog, "student.enrollment.student-name").getText()).isEqualTo("张三");
        assertThat(label(dialog, "student.enrollment.current-class").getText()).isEqualTo("class-1");
        assertThat(combo(dialog, "student.enrollment.department")).isNotNull();
        assertThat(combo(dialog, "student.enrollment.major")).isNotNull();
        assertThat(combo(dialog, "student.enrollment.class")).isNotNull();
        assertThat(field(dialog, "student.enrollment.reason")).isNotNull();
    }

    @Test
    void cascadingDropdownsWork() throws Exception {
        var client = new RecordingStudentClient();
        EnrollmentChangeDialog dialog = dialog(client, profile());

        var deptCall = client.await("STUDENT_LIST_DEPARTMENTS");
        var deptSignal = onEdt(() -> signalModel(combo(dialog, "student.enrollment.department")));
        awaitServiceDependent(deptCall.response());
        client.complete(deptCall, ResponseBody.success(new ArrayList<>(List.of(
                new DepartmentView("d1", "01", "计算机科学与技术学院", true, 1)))));
        await(deptSignal);

        var majorSignal = onEdt(() -> signalModel(combo(dialog, "student.enrollment.major")));
        onEdt(() -> {
            var c = combo(dialog, "student.enrollment.department");
            c.setSelectedIndex(-1);
            c.setSelectedIndex(0);
        });
        var majorCall = client.await("STUDENT_LIST_MAJORS");
        awaitServiceDependent(majorCall.response());
        client.complete(majorCall, ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("m1", "d1", "0101", "计算机科学与技术", true, 1),
                new MajorView("m2", "d1", "0102", "软件工程", true, 1)))));
        await(majorSignal);
        flushEdt();

        assertThat(combo(dialog, "student.enrollment.major").getItemCount()).isEqualTo(2);

        var classSignal = onEdt(() -> signalModel(combo(dialog, "student.enrollment.class")));
        onEdt(() -> {
            var c = combo(dialog, "student.enrollment.major");
            c.setSelectedIndex(-1);
            c.setSelectedIndex(0);
        });
        var classCall = client.await("STUDENT_LIST_CLASSES");
        awaitServiceDependent(classCall.response());
        client.complete(classCall, ResponseBody.success(new ArrayList<>(List.of(
                new ClassView("c1", "m1", "010101", "计科1班", 2024, 1, true, 1),
                new ClassView("c2", "m1", "010102", "计科2班", 2024, 2, true, 1)))));
        await(classSignal);
        flushEdt();

        assertThat(combo(dialog, "student.enrollment.class").getItemCount()).isEqualTo(2);
    }

    @Test
    void successfulSubmissionCallsCallback() throws Exception {
        var client = new RecordingStudentClient();
        var saved = new AtomicReference<StudentView>();
        var savedCount = new AtomicInteger();
        var savedOnEdt = new AtomicBoolean();
        var savedSignal = new CountDownLatch(1);
        EnrollmentChangeDialog dialog = onEdt(() -> displayed(new EnrollmentChangeDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)),
                profile(), value -> {
                    saved.set(value);
                    savedCount.incrementAndGet();
                    savedOnEdt.set(SwingUtilities.isEventDispatchThread());
                    savedSignal.countDown();
                })));
        drainDepartments(client, dialog);

        ClassView targetClass = new ClassView("c2", "m1", "010102", "计科2班", 2024, 2, true, 1);
        onEdt(() -> {
            combo(dialog, "student.enrollment.class").addItem(targetClass);
            combo(dialog, "student.enrollment.class").setSelectedIndex(0);
            field(dialog, "student.enrollment.effective-date").setText("2026-09-01");
            field(dialog, "student.enrollment.reason").setText("转专业");
            button(dialog, "student.enrollment.submit").doClick();
        });
        var call = client.await("STUDENT_UPDATE_ENROLLMENT");
        awaitServiceDependent(call.response());
        StudentView updated = new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "张三", "MALE", null, null, "m1", "c2",
                LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 2);
        client.complete(call, ResponseBody.success(updated));
        assertThat(savedSignal.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(call.body()).isEqualTo(new UpdateStudentEnrollmentCommand(
                "student-1", "c2", LocalDate.of(2026, 9, 1), "转专业", 1));
        assertThat(saved.get().classId()).isEqualTo("c2");
        assertThat(saved.get().rowVersion()).isEqualTo(2);
        assertThat(savedCount).hasValue(1);
        assertThat(savedOnEdt).isTrue();
        assertThat(dialog.isDisplayable()).isFalse();
    }

    @Test
    void concurrentModificationShowsRefreshButton() throws Exception {
        var client = new RecordingStudentClient();
        EnrollmentChangeDialog dialog = dialog(client, profile());
        drainDepartments(client, dialog);

        ClassView targetClass = new ClassView("c2", "m1", "010102", "计科2班", 2024, 2, true, 1);
        onEdt(() -> {
            combo(dialog, "student.enrollment.class").addItem(targetClass);
            combo(dialog, "student.enrollment.class").setSelectedIndex(0);
            field(dialog, "student.enrollment.effective-date").setText("2026-09-01");
            field(dialog, "student.enrollment.reason").setText("转专业");
            button(dialog, "student.enrollment.submit").doClick();
        });
        var call = client.await("STUDENT_UPDATE_ENROLLMENT");
        var conflictSignal = onEdt(() -> signal(label(dialog, "student.enrollment.error"), "text"));
        awaitServiceDependent(call.response());
        client.complete(call, ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null));
        await(conflictSignal);

        assertThat(button(dialog, "student.enrollment.submit").isEnabled()).isFalse();
        assertThat(button(dialog, "student.enrollment.refresh").isVisible()).isTrue();
        assertThat(label(dialog, "student.enrollment.error").getText()).contains("刷新数据");
    }

    @Test
    void refreshAfterConflictReEnablesSubmit() throws Exception {
        var client = new RecordingStudentClient();
        EnrollmentChangeDialog dialog = dialog(client, profile());
        drainDepartments(client, dialog);

        ClassView targetClass = new ClassView("c2", "m1", "010102", "计科2班", 2024, 2, true, 1);
        onEdt(() -> {
            combo(dialog, "student.enrollment.class").addItem(targetClass);
            combo(dialog, "student.enrollment.class").setSelectedIndex(0);
            field(dialog, "student.enrollment.effective-date").setText("2026-09-01");
            field(dialog, "student.enrollment.reason").setText("转专业");
            button(dialog, "student.enrollment.submit").doClick();
        });
        var save = client.await("STUDENT_UPDATE_ENROLLMENT");
        var conflictSignal = onEdt(() -> signal(label(dialog, "student.enrollment.error"), "text"));
        awaitServiceDependent(save.response());
        client.complete(save, ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改，请刷新", null));
        await(conflictSignal);

        onEdt(() -> button(dialog, "student.enrollment.refresh").doClick());
        var refresh = client.await("STUDENT_GET");
        var refreshedSignal = onEdt(() -> signal(label(dialog, "student.enrollment.error"), "text"));
        awaitServiceDependent(refresh.response());
        StudentView refreshed = new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "张三", "MALE", null, null, "m1", "class-1",
                LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 3);
        client.complete(refresh, ResponseBody.success(refreshed));
        await(refreshedSignal);

        assertThat(button(dialog, "student.enrollment.submit").isEnabled()).isTrue();
        assertThat(button(dialog, "student.enrollment.refresh").isVisible()).isFalse();
        assertThat(label(dialog, "student.enrollment.error").getText()).contains("已刷新");
    }

    @Test
    void escapeClosesDialog() throws Exception {
        var client = new RecordingStudentClient();
        EnrollmentChangeDialog dialog = onEdt(() -> displayed(new EnrollmentChangeDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)),
                profile(), ignored -> { })));
        drainDepartments(client, dialog);

        onEdt(() -> {
            JRootPane root = dialog.getRootPane();
            Object binding = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            assertThat(binding).isNotNull();
            Action action = root.getActionMap().get(binding);
            assertThat(action).isNotNull();
            action.actionPerformed(new ActionEvent(dialog, ActionEvent.ACTION_PERFORMED, "escape"));
        });
        flushEdt();

        assertThat(dialog.isDisplayable()).isFalse();
    }

    private static void drainDepartments(RecordingStudentClient client, EnrollmentChangeDialog dialog) throws Exception {
        var deptCall = client.await("STUDENT_LIST_DEPARTMENTS");
        awaitServiceDependent(deptCall.response());
        client.complete(deptCall, ResponseBody.success(new ArrayList<>()));
        flushEdt();
    }

    private static EnrollmentChangeDialog dialog(RecordingStudentClient client, StudentView initial) throws Exception {
        return onEdt(() -> displayed(new EnrollmentChangeDialog(null,
                new StudentClientService(client, Duration.ofSeconds(3)), initial, ignored -> { })));
    }

    private static EnrollmentChangeDialog displayed(EnrollmentChangeDialog dialog) {
        dialog.addNotify();
        return dialog;
    }

    private static StudentView profile() {
        return new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "张三", "MALE", null, null, "major-1", "class-1",
                LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 1);
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(work.call()); }
            catch (Throwable thrown) { failure.set(thrown); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void onEdt(ThrowingRunnable work) throws Exception { onEdt(() -> { work.run(); return null; }); }
    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    private static UiSignal signal(JComponent component, String property) {
        var signal = new UiSignal();
        component.addPropertyChangeListener(property, event -> signal.changed().countDown());
        return signal;
    }

    @SuppressWarnings("unchecked")
    private static UiSignal signalModel(JComboBox<?> combo) {
        var signal = new UiSignal();
        ((javax.swing.ComboBoxModel<Object>) combo.getModel()).addListDataListener(
                new javax.swing.event.ListDataListener() {
                    @Override public void intervalAdded(javax.swing.event.ListDataEvent e) {
                        if (combo.getItemCount() > 0) signal.changed().countDown();
                    }
                    @Override public void intervalRemoved(javax.swing.event.ListDataEvent e) {}
                    @Override public void contentsChanged(javax.swing.event.ListDataEvent e) {
                        if (combo.getItemCount() > 0) signal.changed().countDown();
                    }
                });
        return signal;
    }

    private static void await(UiSignal signal) throws InterruptedException {
        assertThat(signal.changed().await(2, TimeUnit.SECONDS)).isTrue();
    }

    private static void awaitServiceDependent(CompletableFuture<?> response) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (response.getNumberOfDependents() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
        assertThat(response.getNumberOfDependents()).isGreaterThan(0);
    }

    private static JTextField field(Container root, String name) { return component(root, name, JTextField.class); }
    @SuppressWarnings("unchecked")
    private static <T> JComboBox<T> combo(Container root, String name) { return (JComboBox<T>) component(root, name, JComboBox.class); }
    private static JButton button(Container root, String name) { return component(root, name, JButton.class); }
    private static JLabel label(Container root, String name) { return component(root, name, JLabel.class); }
    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        if (name.equals(root.getName()) && type.isInstance(root)) return type.cast(root);
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                try { return component(nested, name, type); }
                catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }

    private static final class UiSignal {
        private final CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch changed() { return changed; }
    }

    private static final class RecordingStudentClient implements StudentRequestClient {
        private final BlockingQueue<Call> calls = new LinkedBlockingQueue<>();

        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            CompletableFuture<ResponseBody<T>> response = new CompletableFuture<>();
            calls.add(new Call(command, body, response));
            return response;
        }

        Call await(String command) throws InterruptedException {
            Call call = calls.poll(2, TimeUnit.SECONDS);
            assertThat(call).isNotNull();
            assertThat(call.command()).isEqualTo(command);
            return call;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        boolean complete(Call call, ResponseBody<?> response) { return ((CompletableFuture) call.response()).complete(response); }
        @SuppressWarnings({"unchecked", "rawtypes"})
        void fail(Call call, Throwable failure) { ((CompletableFuture) call.response()).completeExceptionally(failure); }
    }

    private record Call(String command, Serializable body, CompletableFuture<?> response) { }
}
