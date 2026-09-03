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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StudentDetailPanelTest {
    private ClientConnection connection;

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void changeHistoryRendersAfterProfile() throws Exception {
        var profile = new CompletableFuture<ResponseBody<StudentProfileData>>();
        var changes = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        StudentDetailPanel panel = panel(profile, changes, true);
        SwingUtilities.invokeAndWait(panel::addNotify);
        profile.complete(ResponseBody.success(profile("张三")));
        awaitText(panel, "student.detail.profile.name", "张三");

        var rows = new ArrayList<StudentChangeView>();
        rows.add(new StudentChangeView("c1", "student-1", "ADMISSION", "", "录取",
                "高考录取", "admin-1", LocalDate.of(2024, 8, 1), Instant.parse("2024-08-01T10:00:00Z")));
        rows.add(new StudentChangeView("c2", "student-1", "STATUS_CHANGE", "正常", "休学",
                "个人原因", "admin-2", LocalDate.of(2025, 3, 1), Instant.parse("2025-03-01T10:00:00Z")));
        changes.complete(ResponseBody.success(rows));
        awaitRows(panel, 2);

        JTable table = find(panel, "student.detail.changes", JTable.class);
        assertThat(table.getValueAt(0, 0)).isEqualTo("录取");
        assertThat(table.getValueAt(1, 0)).isEqualTo("状态变更");
        assertThat(table.getValueAt(1, 3)).isEqualTo("个人原因");
    }

    @Test
    void disconnectKeepsProfileVisibleAndDisablesAcademicEditing() throws Exception {
        StudentDetailPanel panel = panel(
                CompletableFuture.completedFuture(ResponseBody.success(profile("张三"))),
                CompletableFuture.completedFuture(ResponseBody.success(new ArrayList<>())), true);
        SwingUtilities.invokeAndWait(panel::addNotify);
        awaitText(panel, "student.detail.profile.name", "张三");
        JButton edit = find(panel, "student.detail.academic.edit", JButton.class);
        assertThat(edit.isEnabled()).isTrue();

        connection.close();
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(edit.isEnabled()).isFalse();
        assertThat(find(panel, "student.detail.profile.idDocumentNumber", JLabel.class).getText())
                .isEqualTo("320101200501010011");
        assertThat(find(panel, "student.detail.status", JLabel.class).getText()).contains("断开");
    }

    @Test
    void responseAfterPanelRemovalIsDiscarded() throws Exception {
        var profile = new CompletableFuture<ResponseBody<StudentProfileData>>();
        StudentDetailPanel panel = panel(profile, new CompletableFuture<>(), true);
        SwingUtilities.invokeAndWait(panel::addNotify);
        SwingUtilities.invokeAndWait(panel::removeNotify);

        profile.complete(ResponseBody.success(profile("迟到响应")));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(find(panel, "student.detail.profile.name", JLabel.class).getText()).isEqualTo("未填写");
    }

    private StudentDetailPanel panel(CompletableFuture<ResponseBody<StudentProfileData>> profile,
            CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>> changes, boolean canEdit) {
        connection = new ClientConnection("localhost", 1);
        setConnected(connection);
        StudentRequestClient requests = new StudentRequestClient() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                if ("STUDENT_GET_PROFILE".equals(command))
                    return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) profile;
                if ("STUDENT_GET_CHANGES".equals(command))
                    return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) changes;
                return CompletableFuture.failedFuture(new UnsupportedOperationException(command));
            }
        };
        return new StudentDetailPanel(new StudentClientService(requests, Duration.ofSeconds(1)),
                connection, "student-1", canEdit);
    }

    private static StudentProfileData profile(String name) {
        StudentView core = new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, name, "MALE", "zhangsan@seu.edu.cn", "13800000000",
                "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 7,
                "计算机学院", "软件工程", "计科2401");
        StudentPersonalProfile personal = new StudentPersonalProfile("ZHANG SAN", null, "共青团员", "汉族",
                "未婚", "居民身份证", "320101200501010011", null, LocalDate.of(2005, 1, 1), "江苏省",
                "中国", "南京市", "南京市", "非农业家庭户口", "南京市", "南京市", "否", "无",
                true, null, false, null, "健康", "A", 58, 172, "魔方", "乒乓球", false,
                "zhangsan@seu.edu.cn", "13800000000");
        StudentAcademicProfile academic = new StudentAcademicProfile("本科生", true, true, "正常", "九龙湖校区",
                "2024", "计算机学院", "软件工程", "计科2401", "本科", "非定向", 4,
                AttendanceMode.RESIDENT, null, null, LocalDate.of(2028, 7, 30), null, null, null, "张航", null);
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
        assertThat(find(root, name, JLabel.class).getText()).contains(expected);
    }

    private static void awaitRows(Container root, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            if (find(root, "student.detail.changes", JTable.class).getRowCount() == expected) return;
            Thread.sleep(10);
        }
        assertThat(find(root, "student.detail.changes", JTable.class).getRowCount()).isEqualTo(expected);
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
}
