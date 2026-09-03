package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.AdminStudentInfoEditDialog;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AdminStudentInfoEditDialogTest {
    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void academicSaveUsesOneAtomicCommand() throws Exception {
        RecordingClient client = new RecordingClient();
        AdminStudentInfoEditDialog dialog = dialog(client, profile());
        loadHierarchy(client, "d1", "m1", "c1", "计算机学院", "软件工程", "计科2401");

        onEdt(() -> {
            field(dialog, "student.info.studentNumber").setText("09024109");
            combo(dialog, "student.info.status").setSelectedItem("休学");
            field(dialog, "student.info.reason").setText("学籍调整");
            button(dialog, "student.info.submit").doClick();
        });

        Call call = client.await("STUDENT_UPDATE_ACADEMIC");
        assertThat(call.body()).isEqualTo(new UpdateStudentAcademicCommand(
                "student-1", "09024109", "c1", StudentType.UNDERGRADUATE, StudentStatus.SUSPENDED,
                true, true, null, null, null, null, AttendanceMode.RESIDENT,
                null, null, null, null, null, null, null, null,
                LocalDate.now(), "学籍调整", 1));
        assertThat(client.hasQueued("STUDENT_CHANGE_STATUS")).isFalse();
    }

    @Test
    void conflictRefreshRebindsAllFiveAcademicControls() throws Exception {
        RecordingClient client = new RecordingClient();
        AdminStudentInfoEditDialog dialog = dialog(client, profile());
        loadHierarchy(client, "d1", "m1", "c1", "计算机学院", "软件工程", "计科2401");
        onEdt(() -> {
            field(dialog, "student.info.studentNumber").setText("09024109");
            combo(dialog, "student.info.status").setSelectedItem("休学");
            field(dialog, "student.info.reason").setText("学籍调整");
            button(dialog, "student.info.submit").doClick();
        });
        client.complete(client.await("STUDENT_UPDATE_ACADEMIC"),
                ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改", null));
        awaitVisible(button(dialog, "student.info.refresh"));

        onEdt(() -> button(dialog, "student.info.refresh").doClick());
        StudentView refreshed = new StudentView("student-1", "user-1", "213240001", "09124122",
                StudentType.UNDERGRADUATE, "张三", "MALE", null, null, "m2", "c2",
                LocalDate.of(2024, 9, 1), StudentStatus.GRADUATED, 8,
                "数学学院", "应用数学", "数学2401");
        client.complete(client.await("STUDENT_GET"), ResponseBody.success(refreshed));
        flushEdt();
        assertThat(button(dialog, "student.info.submit").isEnabled()).isFalse();

        Call departments = client.await("STUDENT_LIST_DEPARTMENTS");
        client.complete(departments, ResponseBody.success(new ArrayList<>(List.of(
                new DepartmentView("d2", "d2", "数学学院", true, 1)))));
        flushEdt();
        assertThat(button(dialog, "student.info.submit").isEnabled()).isFalse();

        Call majors = client.await("STUDENT_LIST_MAJORS");
        client.complete(majors, ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("m2", "d2", "m2", "应用数学", true, 1)))));
        flushEdt();
        assertThat(button(dialog, "student.info.submit").isEnabled()).isFalse();

        Call classes = client.await("STUDENT_LIST_CLASSES");
        client.complete(classes, ResponseBody.success(new ArrayList<>(List.of(
                new ClassView("c2", "m2", "c2", "数学2401", 2024, 1, true, 1)))));
        flushEdt();
        assertThat(button(dialog, "student.info.submit").isEnabled()).isTrue();

        assertThat(field(dialog, "student.info.studentNumber").getText()).isEqualTo("09124122");
        assertThat(combo(dialog, "student.info.status").getSelectedItem()).isEqualTo("已毕业");
        assertThat(((DepartmentView) combo(dialog, "student.info.department").getSelectedItem()).departmentId()).isEqualTo("d2");
        assertThat(((MajorView) combo(dialog, "student.info.major").getSelectedItem()).majorId()).isEqualTo("m2");
        assertThat(((ClassView) combo(dialog, "student.info.class").getSelectedItem()).classId()).isEqualTo("c2");
    }

    @Test
    void slowerPreviousDepartmentResponseCannotOverwriteCurrentSelection() throws Exception {
        RecordingClient client = new RecordingClient();
        AdminStudentInfoEditDialog dialog = dialog(client, profile());
        loadHierarchy(client, "d1", "m1", "c1", "计算机学院", "软件工程", "计科2401");
        DepartmentView second = new DepartmentView("d2", "02", "数学学院", true, 1);
        DepartmentView third = new DepartmentView("d3", "03", "物理学院", true, 1);
        onEdt(() -> {
            combo(dialog, "student.info.department").addItem(second);
            combo(dialog, "student.info.department").addItem(third);
            combo(dialog, "student.info.department").setSelectedItem(second);
            combo(dialog, "student.info.department").setSelectedItem(third);
        });
        Call older = client.await("STUDENT_LIST_MAJORS");
        Call current = client.await("STUDENT_LIST_MAJORS");
        client.complete(current, ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("m3", "d3", "0301", "应用物理", true, 1)))));
        client.complete(older, ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("m2", "d2", "0201", "应用数学", true, 1)))));
        flushEdt();

        JComboBox<?> majors = combo(dialog, "student.info.major");
        assertThat(java.util.stream.IntStream.range(0, majors.getItemCount())
                .mapToObj(majors::getItemAt).filter(MajorView.class::isInstance)
                .map(MajorView.class::cast).map(MajorView::majorId))
                .containsExactly("m3");
    }

    private static AdminStudentInfoEditDialog dialog(RecordingClient client, StudentView profile) throws Exception {
        return onEdt(() -> {
            AdminStudentInfoEditDialog dialog = new AdminStudentInfoEditDialog(null,
                    new StudentClientService(client, Duration.ofSeconds(2)), profile,
                    academic(), ignored -> {});
            dialog.addNotify();
            return dialog;
        });
    }

    private static StudentAcademicProfile academic() {
        return new StudentAcademicProfile("本科生", true, true, "正常", null,
                "2024", "计算机学院", "软件工程", "计科2401", null, null, null,
                AttendanceMode.RESIDENT, null, null, null, null, null, null,
                null, null);
    }

    private static void loadHierarchy(RecordingClient client, String departmentId, String majorId,
            String classId, String departmentName, String majorName, String className) throws Exception {
        Call departments = client.await("STUDENT_LIST_DEPARTMENTS");
        assertThat(departments.body()).isEqualTo(new ActiveOnlyQuery(false));
        client.complete(departments, ResponseBody.success(new ArrayList<>(List.of(
                new DepartmentView(departmentId, departmentId, departmentName, true, 1)))));
        Call majors = client.await("STUDENT_LIST_MAJORS");
        assertThat(majors.body()).isEqualTo(new OrganizationChildrenQuery(departmentId, false));
        client.complete(majors, ResponseBody.success(new ArrayList<>(List.of(
                new MajorView(majorId, departmentId, majorId, majorName, true, 1)))));
        Call classes = client.await("STUDENT_LIST_CLASSES");
        assertThat(classes.body()).isEqualTo(new OrganizationChildrenQuery(majorId, false));
        client.complete(classes, ResponseBody.success(new ArrayList<>(List.of(
                new ClassView(classId, majorId, classId, className, 2024, 1, true, 1)))));
        flushEdt();
    }

    private static StudentView profile() {
        return new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "张三", "MALE", null, null, "m1", "c1",
                LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 1,
                "计算机学院", "软件工程", "计科2401");
    }

    private static void awaitVisible(Component component) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            flushEdt();
            if (component.isVisible()) return;
            Thread.sleep(10);
        }
        assertThat(component.isVisible()).isTrue();
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { value.set(task.call()); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return value.get();
    }
    private static void onEdt(Runnable task) throws Exception { SwingUtilities.invokeAndWait(task); }
    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
    private static JTextField field(Container root, String name) { return component(root, name, JTextField.class); }
    @SuppressWarnings("unchecked") private static <T> JComboBox<T> combo(Container root, String name) {
        return (JComboBox<T>) component(root, name, JComboBox.class);
    }
    private static JButton button(Container root, String name) { return component(root, name, JButton.class); }
    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                try { return component(nested, name, type); } catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static final class RecordingClient implements StudentRequestClient {
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
        boolean hasQueued(String command) { return calls.stream().anyMatch(call -> command.equals(call.command())); }
        @SuppressWarnings({"rawtypes", "unchecked"}) void complete(Call call, ResponseBody<?> response) {
            ((CompletableFuture) call.response()).complete(response);
        }
    }
    private record Call(String command, Serializable body, CompletableFuture<?> response) { }
}
