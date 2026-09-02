package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.StudentAdmissionDialog;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAdmissionDialogTest {
    @AfterEach void disposeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test void formFieldsExistAndAreInteractive() throws Exception {
        var client = new RecordingStudentClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        StudentAdmissionDialog dialog = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();

        assertThat(field(dialog, "student.admission.name")).isInstanceOf(JTextField.class);
        assertThat(combo(dialog, "student.admission.gender")).isInstanceOf(JComboBox.class);
        assertThat(combo(dialog, "student.admission.type")).isInstanceOf(JComboBox.class);
        assertThat(combo(dialog, "student.admission.department")).isInstanceOf(JComboBox.class);
        assertThat(combo(dialog, "student.admission.major")).isInstanceOf(JComboBox.class);
        assertThat(combo(dialog, "student.admission.class")).isInstanceOf(JComboBox.class);
        assertThat(component(dialog, "student.admission.year", JSpinner.class)).isInstanceOf(JSpinner.class);
        assertThat(field(dialog, "student.admission.email")).isInstanceOf(JTextField.class);
        assertThat(field(dialog, "student.admission.phone")).isInstanceOf(JTextField.class);
        assertThat(button(dialog, "student.admission.submit")).isInstanceOf(JButton.class);
        assertThat(button(dialog, "student.admission.cancel")).isInstanceOf(JButton.class);

        for (String name : new String[]{"student.admission.name", "student.admission.gender",
                "student.admission.type", "student.admission.department", "student.admission.major",
                "student.admission.class", "student.admission.year", "student.admission.email",
                "student.admission.phone", "student.admission.submit", "student.admission.cancel"}) {
            assertThat(component(dialog, name, JComponent.class)
                    .getAccessibleContext().getAccessibleName()).isNotBlank();
        }
    }

    @Test void cascadingDropdownsWork() throws Exception {
        var client = new RecordingStudentClient();
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new DepartmentView("dept-1", "D01", "计算机科学与工程学院", true, 1)))));
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("maj-1", "dept-1", "M01", "计算机科学与技术", true, 1)))));
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new ClassView("cls-1", "maj-1", "C01", "计科2401", 2024, 1, true, 1)))));

        StudentAdmissionDialog dialog = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();

        JComboBox<DepartmentView> departments = combo(dialog, "student.admission.department");
        JComboBox<MajorView> majors = combo(dialog, "student.admission.major");
        JComboBox<ClassView> classes = combo(dialog, "student.admission.class");

        var majorReady = signalModel(majors);
        onEdtRun(() -> departments.setSelectedIndex(1));
        flushEdt();
        await(majorReady);

        var classReady = signalModel(classes);
        onEdtRun(() -> majors.setSelectedIndex(1));
        flushEdt();
        await(classReady);

        assertThat(departments.getItemCount()).isGreaterThan(1);
        assertThat(majors.getItemCount()).isGreaterThan(1);
        assertThat(classes.getItemCount()).isGreaterThan(1);
    }

    @Test void successfulSubmissionShowsSuccessPanel() throws Exception {
        var client = new RecordingStudentClient();
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new DepartmentView("dept-1", "D01", "计算机科学与工程学院", true, 1)))));
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("maj-1", "dept-1", "M01", "计算机科学与技术", true, 1)))));
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new ClassView("cls-1", "maj-1", "C01", "计科2401", 2024, 1, true, 1)))));
        StudentView sv = new StudentView("student-new", "user-new", "09024101", "213240001",
                StudentType.UNDERGRADUATE, "张三", "MALE", null, null, "maj-1", "cls-1",
                null, StudentStatus.ACTIVE, 1, "计算机学院", "软件工程", "计科2401");
        client.enqueue(ResponseBody.success(new StudentAdmissionResult(sv, "09024101", "213240001", true)));

        StudentAdmissionDialog dialog = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();

        onEdtRun(() -> {
            type(field(dialog, "student.admission.name"), "张三");
            combo(dialog, "student.admission.gender").setSelectedIndex(1);
            combo(dialog, "student.admission.type").setSelectedIndex(1);
        });

        var majorReady = signalModel(combo(dialog, "student.admission.major"));
        onEdtRun(() -> combo(dialog, "student.admission.department").setSelectedIndex(1));
        flushEdt();
        await(majorReady);

        var classReady = signalModel(combo(dialog, "student.admission.class"));
        onEdtRun(() -> combo(dialog, "student.admission.major").setSelectedIndex(1));
        flushEdt();
        await(classReady);

        onEdtRun(() -> {
            combo(dialog, "student.admission.class").setSelectedIndex(1);
            button(dialog, "student.admission.submit").doClick();
        });
        flushEdt();

        assertThat(component(dialog, "student.admission.success.campus-card", JLabel.class).getText())
                .contains("09024101");
        assertThat(component(dialog, "student.admission.success.student-number", JLabel.class).getText())
                .contains("213240001");
        assertThat(component(dialog, "student.admission.success.password-hint", JLabel.class).getText())
                .contains("12345678");
        assertThat(button(dialog, "student.admission.success.close")).isNotNull();
    }

    @Test void validationRejectsBlankName() throws Exception {
        var client = new RecordingStudentClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        StudentAdmissionDialog dialog = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();

        onEdtRun(() -> button(dialog, "student.admission.submit").doClick());
        flushEdt();

        assertThat(client.consumed.get()).isEqualTo(1);
        assertThat(label(dialog, "student.admission.error").getText()).contains("姓名");
    }

    @Test void cancelAndEscapeCloseDialog() throws Exception {
        var client = new RecordingStudentClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        StudentAdmissionDialog dialog = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();
        onEdtRun(() -> button(dialog, "student.admission.cancel").doClick());
        flushEdt();
        assertThat(dialog.isDisplayable()).isFalse();

        client.enqueue(ResponseBody.success(new ArrayList<>()));
        StudentAdmissionDialog dialog2 = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();
        onEdtRun(() -> {
            JRootPane root = dialog2.getRootPane();
            Object binding = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            assertThat(binding).isNotNull();
            Action action = root.getActionMap().get(binding);
            assertThat(action).isNotNull();
            action.actionPerformed(new ActionEvent(dialog2, ActionEvent.ACTION_PERFORMED, "escape"));
        });
        flushEdt();
        assertThat(dialog2.isDisplayable()).isFalse();
    }

    @Test void formDoesNotContainGeneratedIdentifiers() throws Exception {
        var client = new RecordingStudentClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        StudentAdmissionDialog dialog = onEdt(() -> displayed(new StudentAdmissionDialog(
                null, new StudentClientService(client, Duration.ofSeconds(3)))));
        flushEdt();

        boolean hasCampusCardInput = false;
        boolean hasStudentNumberInput = false;
        boolean hasUserIdInput = false;
        boolean hasPasswordInput = false;
        for (Component c : dialog.getContentPane().getComponents()) {
            if (c instanceof Container container) {
                for (Component inner : container.getComponents()) {
                    String name = inner.getName();
                    if (name == null) continue;
                    if (name.contains("campus-card") && !(inner instanceof JLabel)) hasCampusCardInput = true;
                    if (name.contains("student-number") && !(inner instanceof JLabel)) hasStudentNumberInput = true;
                    if (name.contains("user-id") || name.contains("userId")) hasUserIdInput = true;
                    if (name.contains("password") && !(inner instanceof JLabel)) hasPasswordInput = true;
                }
            }
        }
        assertThat(hasCampusCardInput).isFalse();
        assertThat(hasStudentNumberInput).isFalse();
        assertThat(hasUserIdInput).isFalse();
        assertThat(hasPasswordInput).isFalse();
    }

    private static StudentAdmissionDialog displayed(StudentAdmissionDialog dialog) {
        dialog.addNotify();
        return dialog;
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Throwable thrown) { failure.set(thrown); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void onEdtRun(ThrowingRunnable work) throws Exception { onEdt(() -> { work.run(); return null; }); }
    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

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

    private static void type(JTextField field, String text) {
        field.setText("");
        field.setText(text);
    }

    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
    private static final class UiSignal {
        private final CountDownLatch changed = new CountDownLatch(1);
        CountDownLatch changed() { return changed; }
    }

    private static final class RecordingStudentClient implements StudentRequestClient {
        private final BlockingQueue<ResponseBody<?>> pending = new LinkedBlockingQueue<>();
        private final AtomicInteger consumed = new AtomicInteger();

        void enqueue(ResponseBody<?> response) { pending.add(response); }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            ResponseBody<?> response = pending.poll();
            consumed.incrementAndGet();
            CompletableFuture<ResponseBody<T>> future = new CompletableFuture<>();
            if (response != null) future.complete((ResponseBody) response);
            return future;
        }

        void awaitResponse() throws InterruptedException {
            int before = consumed.get();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (consumed.get() == before && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(consumed.get()).isGreaterThan(before);
        }
    }
}
