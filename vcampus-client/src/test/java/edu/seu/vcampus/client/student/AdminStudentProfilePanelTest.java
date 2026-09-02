package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.StudentDetailPanel;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AdminStudentProfilePanelTest {
    private ClientConnection connection;

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void administratorSeesTheCompleteApprovedProfile() throws Exception {
        StudentDetailPanel panel = panel(true);
        SwingUtilities.invokeAndWait(panel::addNotify);
        awaitText(panel, "student.detail.profile.namePinyin", "ZHANG SAN");

        assertThat(text(panel)).contains(
                "个人基本信息", "学籍信息", "政治面貌", "身份证件号",
                "入学前户口所在地", "是否独生子女", "培养方式", "预计毕业日期",
                "学生来源", "辅导员联系方式");
        assertThat(label(panel, "student.detail.profile.idDocumentNumber").getText())
                .isEqualTo("320101200501010011");
        assertThat(label(panel, "student.detail.profile.attendanceMode").getText()).isEqualTo("住校");
        assertThat(label(panel, "student.detail.profile.department").getText())
                .isEqualTo("计算机科学与工程学院");
        assertThat(find(panel, "student.detail.changes", JTable.class)).isNotNull();
    }

    @Test
    void academicHeaderContainsTheOnlyEditAction() throws Exception {
        StudentDetailPanel panel = panel(true);
        SwingUtilities.invokeAndWait(panel::addNotify);
        awaitText(panel, "student.detail.profile.name", "张三");
        SwingUtilities.invokeAndWait(() -> layout(panel, 1024, 700));

        JLabel title = label(panel, "student.detail.academic.title");
        JButton edit = find(panel, "student.detail.academic.edit", JButton.class);
        assertThat(edit).isNotNull();
        assertThat(edit.getParent()).isSameAs(title.getParent());
        assertThat(Arrays.asList(title.getParent().getComponents()).indexOf(edit))
                .isGreaterThan(Arrays.asList(title.getParent().getComponents()).indexOf(title));
        assertThat(all(panel, JButton.class).stream().filter(button -> "编辑".equals(button.getText())))
                .containsExactly(edit);
        assertThat(find(panel, "student.detail.personal.edit", JButton.class)).isNull();
        assertThat(find(panel, "student.detail.change-status", JButton.class)).isNull();
    }

    @Test
    void teacherViewerKeepsLimitedDetailWithoutSensitiveFieldsOrEditing() throws Exception {
        StudentDetailPanel panel = panel(false);
        SwingUtilities.invokeAndWait(panel::addNotify);
        awaitText(panel, "student.detail.profile.name", "张三");

        assertThat(find(panel, "student.detail.academic.edit", JButton.class)).isNull();
        assertThat(label(panel, "student.detail.profile.idDocumentNumber").getText()).isEqualTo("未填写");
        assertThat(label(panel, "student.detail.profile.department").getText()).isEqualTo("计算机学院");
    }

    private StudentDetailPanel panel(boolean canEdit) {
        connection = new ClientConnection("localhost", 1);
        setConnected(connection);
        StudentRequestClient requests = new StudentRequestClient() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                if ("STUDENT_GET_PROFILE".equals(command)) {
                    return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>)
                            CompletableFuture.completedFuture(ResponseBody.success(profile()));
                }
                if ("STUDENT_GET".equals(command)) {
                    return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>)
                            CompletableFuture.completedFuture(ResponseBody.success(profile().core()));
                }
                if ("STUDENT_GET_CHANGES".equals(command)) {
                    return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>)
                            CompletableFuture.completedFuture(ResponseBody.success(new ArrayList<StudentChangeView>()));
                }
                return CompletableFuture.failedFuture(new UnsupportedOperationException(command));
            }
        };
        return new StudentDetailPanel(new StudentClientService(requests, Duration.ofSeconds(1)),
                connection, "student-1", canEdit);
    }

    private static StudentProfileData profile() {
        StudentView core = new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "张三", "MALE", "zhangsan@seu.edu.cn", "13800000000",
                "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 7,
                "计算机学院", "软件工程", "计科2401");
        StudentPersonalProfile personal = new StudentPersonalProfile("ZHANG SAN", "张山", "共青团员", "汉族",
                "未婚", "居民身份证", "320101200501010011", LocalDate.of(2021, 1, 1),
                LocalDate.of(2005, 1, 1), "江苏省", "中国", "南京市", "南京市", "非农业家庭户口",
                "南京市", "南京市", "否", "无", true, LocalDate.of(2020, 5, 4), false,
                null, "健康", "A", 58, 172, "魔方", "乒乓球", false,
                "zhangsan@seu.edu.cn", "13800000000");
        StudentAcademicProfile academic = new StudentAcademicProfile("本科生", true, true, "正常", "九龙湖校区",
                "2024", "计算机科学与工程学院", "计算机科学与技术", "090241", "本科", "非定向", 4,
                AttendanceMode.RESIDENT, "工学学士", "本科", LocalDate.of(2028, 7, 30), null,
                "普通高中", null, "张航", "025-12345678");
        return new StudentProfileData(core, personal, academic);
    }

    private static void awaitText(Container root, String name, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            JLabel value = find(root, name, JLabel.class);
            if (value != null && value.getText().contains(expected)) return;
            Thread.sleep(10);
        }
        JLabel value = find(root, name, JLabel.class);
        assertThat(value).isNotNull();
        assertThat(value.getText()).contains(expected);
    }

    private static void setConnected(ClientConnection value) {
        try {
            var field = ClientConnection.class.getDeclaredField("state");
            field.setAccessible(true);
            field.set(value, ConnectionState.CONNECTED);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static JLabel label(Container root, String name) {
        return find(root, name, JLabel.class);
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        if (name.equals(root.getName())) return type.cast(root);
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return type.cast(component);
            if (component instanceof Container nested) {
                T found = find(nested, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> java.util.List<T> all(Container root, Class<T> type) {
        var result = new ArrayList<T>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) result.add(type.cast(component));
            if (component instanceof Container nested) result.addAll(all(nested, type));
        }
        return result;
    }

    private static String text(Component component) {
        if (component instanceof JLabel label) return label.getText();
        if (component instanceof AbstractButton button) return button.getText();
        if (component instanceof Container container) {
            StringBuilder result = new StringBuilder();
            for (Component child : container.getComponents()) result.append(text(child)).append(' ');
            return result.toString();
        }
        return "";
    }

    private static void layout(Container root, int width, int height) {
        root.setBounds(0, 0, width, height);
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) layout(nested, child.getWidth(), child.getHeight());
        }
    }
}
