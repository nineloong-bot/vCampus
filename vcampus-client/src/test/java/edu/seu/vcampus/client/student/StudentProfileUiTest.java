package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.MyStudentProfilePanel;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileUiTest {
    private static final java.util.List<ClientConnection> connections = new java.util.ArrayList<>();
    @AfterEach void closeConnections() { connections.forEach(ClientConnection::close); connections.clear(); }
    @Test void profileKeepsAcademicShellAndRendersStudentIdentityOnEdt() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);
        assertThat(fixture.button("student.profile.edit").isEnabled()).isFalse();
        assertThat(fixture.label("student.profile.status").getText()).contains("正在加载");
        var updatedOnEdt = new AtomicBoolean();
        fixture.label("student.profile.email").addPropertyChangeListener("text", e -> updatedOnEdt.set(SwingUtilities.isEventDispatchThread()));
        Thread completion = new Thread(() -> response.complete(ResponseBody.success(profile(7, "zhangsan@seu.edu.cn", "13800000000"))));
        completion.start(); completion.join();
        fixture.flushEdt();
        assertThat(fixture.visibleText()).contains("我的学籍档案", "张三", "213240001", "09024101", "正常",
                "本科生", "2024-09-01", "major-1", "class-1", "zhangsan@seu.edu.cn", "13800000000");
        assertThat(fixture.button("student.profile.edit").isEnabled()).isTrue();
        assertThat(updatedOnEdt).isTrue();
    }

    @Test void nullContactValuesRenderAsNotFilled() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);
        fixture.awaitDispatch(1); response.complete(ResponseBody.success(profileWithNulls())); fixture.flushEdt();
        assertThat(fixture.label("student.profile.name").getText()).isEqualTo("张三");
        assertThat(fixture.label("student.profile.email").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.phone").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.type").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.lifecycle").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.enrollment").getText()).isEqualTo("未填写");
    }

    @Test void lifecycleStatusesRenderChineseLabels() throws Exception {
        for (var status : new StudentStatus[]{StudentStatus.SUSPENDED, StudentStatus.GRADUATED, StudentStatus.WITHDRAWN}) {
            var response = new CompletableFuture<ResponseBody<StudentView>>();
            var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
            SwingUtilities.invokeAndWait(fixture::showProfile);
            response.complete(ResponseBody.success(profile(status))); fixture.flushEdt();
            assertThat(fixture.visibleText()).contains(status == StudentStatus.SUSPENDED ? "休学" : status == StudentStatus.GRADUATED ? "已毕业" : "已退学");
        }
    }

    @Test void refreshKeepsOldValuesWhileLoading() throws Exception {
        var first = CompletableFuture.completedFuture(ResponseBody.success(profile(1, "old@seu.edu.cn", "1")));
        var second = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(first, second, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.flushEdt();
        SwingUtilities.invokeAndWait(() -> fixture.panel.refreshProfile());
        assertThat(fixture.visibleText()).contains("old@seu.edu.cn", "正在加载");
        assertThat(fixture.button("student.profile.refresh").isEnabled()).isFalse();
        assertThat(fixture.button("student.profile.edit").isEnabled()).isFalse();
    }

    @Test void newerRefreshWinsOverStaleResponse() throws Exception {
        var first = new CompletableFuture<ResponseBody<StudentView>>();
        var second = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(first, second, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.awaitDispatch(1); fixture.flushEdt();
        SwingUtilities.invokeAndWait(() -> fixture.panel.refreshProfile());
        fixture.awaitDispatch(2);
        second.complete(ResponseBody.success(profile(2, "new@seu.edu.cn", "new-phone"))); fixture.flushEdt();
        first.complete(ResponseBody.success(profile(1, "stale@seu.edu.cn", "stale-phone"))); fixture.flushEdt();
        assertThat(fixture.visibleText()).contains("new@seu.edu.cn", "new-phone").doesNotContain("stale@");
    }

    @Test void failedFutureUsesSafeGenericMessage() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); response.completeExceptionally(new IllegalStateException("secret")); fixture.flushEdt();
        assertThat(fixture.visibleText()).contains("档案加载失败，请稍后重试").doesNotContain("secret");
    }

    @Test void failedBodyShowsBusinessMessageAndRetry() throws Exception {
        var initial = CompletableFuture.completedFuture(ResponseBody.success(profile(1, "loaded@seu.edu.cn", "phone")));
        var failed = CompletableFuture.completedFuture(ResponseBody.<StudentView>failure("DENIED", "没有权限", null));
        var fixture = new StudentUiFixture(initial, failed, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.awaitDispatch(1); fixture.flushEdt();
        assertThat(fixture.label("student.profile.email").getText()).isEqualTo("loaded@seu.edu.cn");
        assertThat(fixture.button("student.profile.refresh").getText()).isEqualTo("刷新");
        SwingUtilities.invokeAndWait(() -> fixture.panel.refreshProfile()); fixture.awaitDispatch(2); fixture.flushEdt();
        assertThat(fixture.visibleText()).contains("没有权限");
        assertThat(fixture.button("student.profile.refresh").getText()).isEqualTo("重试");
        assertThat(fixture.button("student.profile.refresh").isEnabled()).isTrue();
    }

    @Test void disconnectedStateRetainsLoadedValuesAndDisablesEdit() throws Exception {
        var response = CompletableFuture.completedFuture(ResponseBody.success(profile(1, "x@y", "2")));
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.flushEdt();
        fixture.connection.close(); fixture.flushEdt();
        assertThat(fixture.visibleText()).contains("x@y");
        assertThat(fixture.button("student.profile.edit").isEnabled()).isFalse();
    }

    @Test void lateResponseAfterRemoveNotifyDoesNotChangeLabels() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);
        SwingUtilities.invokeAndWait(fixture.panel::removeNotify);
        response.complete(ResponseBody.success(profile(9, "late", "late"))); fixture.flushEdt();
        assertThat(fixture.visibleText()).doesNotContain("late");
    }

    private static StudentView profile(long version, String email, String phone) {
        return new StudentView("student-1", "user-1", "213240001", "09024101", StudentType.UNDERGRADUATE,
                "张三", "MALE", email, phone, "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, version);
    }
    private static StudentView profile(StudentStatus status) { var p = profile(1, "a", "b"); return new StudentView(p.studentId(), p.userId(), p.campusCardNumber(), p.studentNumber(), p.studentType(), p.studentName(), p.gender(), p.email(), p.phone(), p.majorId(), p.classId(), p.enrollmentDate(), status, p.rowVersion()); }
    private static StudentView profileWithNulls() { var p = profile(1, null, null); return new StudentView(p.studentId(), p.userId(), p.campusCardNumber(), p.studentNumber(), null, p.studentName(), p.gender(), null, null, p.majorId(), p.classId(), null, null, p.rowVersion()); }

    private static final class StudentUiFixture {
        final ClientConnection connection = new ClientConnection("localhost", 1); final StudentClientService students;
        final CompletableFuture<ResponseBody<StudentView>>[] responses;
        final AtomicInteger sendCount = new AtomicInteger();
        final CountDownLatch[] dispatches = {new CountDownLatch(1), new CountDownLatch(1)};
        MyStudentProfilePanel panel;
        StudentUiFixture(CompletableFuture<ResponseBody<StudentView>> response, ConnectionState state) { this(response, new CompletableFuture[0]); setState(state); }
        StudentUiFixture(CompletableFuture<ResponseBody<StudentView>> response, CompletableFuture<ResponseBody<StudentView>> next, ConnectionState state) { this(response, next); setState(state); }
        @SafeVarargs StudentUiFixture(CompletableFuture<ResponseBody<StudentView>> response, CompletableFuture<ResponseBody<StudentView>>... next) {
            connections.add(connection);
            this.responses = new CompletableFuture[next.length + 1]; this.responses[0] = response; System.arraycopy(next, 0, responses, 1, next.length);
            StudentRequestClient client = new StudentRequestClient() {
                @SuppressWarnings("unchecked") public <T extends java.io.Serializable> CompletableFuture<ResponseBody<T>> send(String command, java.io.Serializable body, Duration timeout) { int call = sendCount.incrementAndGet(); if (call <= dispatches.length) dispatches[call - 1].countDown(); return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) responses[Math.min(index++, responses.length - 1)]; }
            }; students = new StudentClientService(client, Duration.ofSeconds(1));
        }
        void setState(ConnectionState state) { try { var f = ClientConnection.class.getDeclaredField("state"); f.setAccessible(true); f.set(connection, state); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
        int index;
        void awaitDispatch(int count) throws InterruptedException { assertThat(count).isBetween(1, dispatches.length); assertThat(dispatches[count - 1].await(2, TimeUnit.SECONDS)).isTrue(); }
        void showProfile() { panel = new MyStudentProfilePanel(students, connection); panel.addNotify(); }
        void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
        String visibleText() { return text(panel); }
        <T extends Component> T component(String name, Class<T> type) { return type.cast(find(panel, name)); }
        JButton button(String name) { return component(name, JButton.class); }
        JLabel label(String name) { return component(name, JLabel.class); }
        static String text(Component c) { if (c instanceof JLabel l) return l.getText() + " " + l.getName(); if (c instanceof AbstractButton b) return b.getText(); if (c instanceof Container p) { var s = new StringBuilder(); for (var x : p.getComponents()) s.append(text(x)).append(' '); return s.toString(); } return ""; }
        static Component find(Container p, String n) { if (n.equals(p.getName())) return p; for (var c : p.getComponents()) { if (n.equals(c.getName())) return c; if (c instanceof Container q) { var x = find(q, n); if (x != null) return x; } } return null; }
    }
}
