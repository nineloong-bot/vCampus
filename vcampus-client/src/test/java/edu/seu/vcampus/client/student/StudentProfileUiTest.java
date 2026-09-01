package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.AttendanceModeEditPanel;
import edu.seu.vcampus.client.student.ui.MyStudentProfilePanel;
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
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileUiTest {
    private ClientConnection connection;

    @AfterEach void close() throws Exception {
        if (connection != null) connection.close();
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test void profileMatchesTwoSectionWorkflowAndRendersDraftValues() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentProfileWorkspace>>();
        var dispatched = new CountDownLatch(1);
        MyStudentProfilePanel panel = panel(response, dispatched);
        SwingUtilities.invokeAndWait(panel::addNotify);
        assertThat(dispatched.await(2, TimeUnit.SECONDS)).isTrue();
        response.complete(ResponseBody.success(workspace(StudentProfileApplicationStatus.DRAFT, null)));
        awaitText(panel, "student.profile.name", "正式姓名");

        assertThat(text(panel)).contains("个人基本信息", "学籍信息", "暂存姓名", "走读", "已暂存，尚未提交审核");
        assertThat(button(panel, "student.profile.personal.edit").getText()).isEqualTo("编辑");
        assertThat(button(panel, "student.profile.academic.edit").getText()).isEqualTo("编辑");
        assertThat(button(panel, "student.profile.export").getText()).contains("导出");
        assertThat(button(panel, "student.profile.submit").getText()).isEqualTo("提交审核");
        assertThat(button(panel, "student.profile.submit").isEnabled()).isTrue();
    }

    @Test void pendingLocksEditingAndRejectedReasonIsVisible() throws Exception {
        MyStudentProfilePanel pending = panel(CompletableFuture.completedFuture(
                ResponseBody.success(workspace(StudentProfileApplicationStatus.PENDING, null))), new CountDownLatch(0));
        SwingUtilities.invokeAndWait(pending::addNotify);
        awaitText(pending, "student.profile.application.status", "审核中");
        assertThat(button(pending, "student.profile.personal.edit").isEnabled()).isFalse();
        assertThat(button(pending, "student.profile.academic.edit").isEnabled()).isFalse();
        assertThat(button(pending, "student.profile.submit").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(pending::removeNotify);
        MyStudentProfilePanel rejected = panel(CompletableFuture.completedFuture(
                ResponseBody.success(workspace(StudentProfileApplicationStatus.REJECTED, "身份证签发日期缺失"))), new CountDownLatch(0));
        SwingUtilities.invokeAndWait(rejected::addNotify);
        awaitText(rejected, "student.profile.application.status", "身份证签发日期缺失");
        assertThat(text(rejected)).contains("已驳回", "身份证签发日期缺失");
    }

    @Test void academicEditorExposesOnlyRequiredAttendanceDropdown() {
        AttendanceModeEditPanel editor = new AttendanceModeEditPanel(AttendanceMode.RESIDENT);
        JComboBox<?> combo = find(editor, "student.profile.attendance.mode", JComboBox.class);
        assertThat(combo.getItemCount()).isEqualTo(4);
        assertThat(java.util.stream.IntStream.range(0, combo.getItemCount()).mapToObj(combo::getItemAt)
                .map(item -> ((AttendanceMode) item).displayName()))
                .containsExactly("走读", "住校", "借宿", "其他");
        assertThat(all(editor, JTextField.class)).isEmpty();
        assertThat(editor.selectedMode()).isEqualTo(AttendanceMode.RESIDENT);
    }

    private MyStudentProfilePanel panel(CompletableFuture<ResponseBody<StudentProfileWorkspace>> response,
            CountDownLatch dispatched) {
        connection = new ClientConnection("localhost", 1); setConnected(connection);
        StudentRequestClient requests = new StudentRequestClient() {
            @Override @SuppressWarnings("unchecked") public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                dispatched.countDown();
                return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) response;
            }
        };
        return new MyStudentProfilePanel(new StudentClientService(requests, Duration.ofSeconds(1)), connection);
    }

    private static StudentProfileWorkspace workspace(StudentProfileApplicationStatus status, String reason) {
        StudentView core = new StudentView("student-1", "user-1", "213240001", "09024101",
                StudentType.UNDERGRADUATE, "正式姓名", "男", "formal@seu.edu.cn", "13800000000",
                "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, 7);
        StudentPersonalProfile personal = new StudentPersonalProfile("ZHENGSHI", null, "共青团员", "汉族",
                "未婚", "居民身份证", "320101200501010011", null, LocalDate.of(2005, 1, 1), "江苏省",
                "中国", "南京市", "南京市", "非农业家庭户口", "南京市", "南京市", "否", "无",
                true, null, false, null, "健康", "A", 58, 172, "魔方", "乒乓球", false,
                "formal@seu.edu.cn", "13800000000");
        StudentAcademicProfile academic = new StudentAcademicProfile("本科生", true, true, "正常", "九龙湖校区",
                "2024", "计算机科学与工程学院", "计算机科学与技术", "090241", "本科", "非定向", 4,
                AttendanceMode.RESIDENT, null, null, LocalDate.of(2028, 7, 30), null, null, null, "张航", null);
        StudentPersonalProfile draft = new StudentPersonalProfile(personal.namePinyin(), "暂存姓名", personal.politicalStatus(),
                personal.ethnicity(), personal.maritalStatus(), personal.idDocumentType(), personal.idDocumentNumber(),
                personal.idIssuedDate(), personal.birthDate(), personal.nativePlace(), personal.countryRegion(), personal.birthplace(),
                personal.studentOriginPlace(), personal.householdRegistrationType(), personal.householdBeforeEnrollment(),
                personal.householdAfterEnrollment(), personal.overseasChineseStatus(), personal.religion(), personal.leagueMember(),
                personal.leagueJoinDate(), personal.partyMember(), personal.partyJoinDate(), personal.healthStatus(), personal.bloodType(),
                personal.weightKg(), personal.heightCm(), personal.specialties(), personal.hobbies(), personal.onlyChild(),
                "draft@seu.edu.cn", personal.phone());
        StudentProfileApplicationView application = new StudentProfileApplicationView("app-1", "student-1", status,
                draft, AttendanceMode.DAY_STUDENT, 7, 2, Instant.now(), null, null, reason, Instant.now(), Instant.now());
        return new StudentProfileWorkspace(new StudentProfileData(core, personal, academic), application);
    }

    private static void awaitText(Container root, String name, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            JLabel label = find(root, name, JLabel.class);
            if (label != null && label.getText().contains(expected)) return;
            Thread.sleep(10);
        }
        assertThat(find(root, name, JLabel.class).getText()).contains(expected);
    }
    private static void setConnected(ClientConnection value) {
        try { var field = ClientConnection.class.getDeclaredField("state"); field.setAccessible(true); field.set(value, ConnectionState.CONNECTED); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static JButton button(Container root, String name) { return find(root, name, JButton.class); }
    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return type.cast(component);
            if (component instanceof Container nested) { T found = find(nested, name, type); if (found != null) return found; }
        }
        return null;
    }
    private static <T extends Component> java.util.List<T> all(Container root, Class<T> type) {
        var result = new java.util.ArrayList<T>();
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
            var result = new StringBuilder(); for (Component child : container.getComponents()) result.append(text(child)).append(' ');
            return result.toString();
        }
        return "";
    }
}
