package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.MyStudentProfilePanel;
import edu.seu.vcampus.client.student.ui.UpdateContactDialog;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class StudentProfileUiTest {
    private static final java.util.List<ClientConnection> connections = new java.util.ArrayList<>();
    @AfterEach void closeConnections() throws Exception { connections.forEach(ClientConnection::close); connections.clear(); SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose)); }
    @Test void profileKeepsAcademicShellAndRendersStudentIdentityOnEdt() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);
        assertThat(fixture.button("student.profile.edit").isEnabled()).isFalse();
        assertThat(fixture.label("student.profile.status").getText()).contains("正在加载");
        var updatedOnEdt = new AtomicBoolean();
        fixture.label("student.profile.email").addPropertyChangeListener("text", e -> updatedOnEdt.set(SwingUtilities.isEventDispatchThread()));
        fixture.awaitDispatch(1);
        var rendered = onEdt(() -> fixture.observeText("student.profile.email"));
        Thread completion = new Thread(() -> response.complete(ResponseBody.success(profile(7, "zhangsan@seu.edu.cn", "13800000000"))));
        completion.start(); completion.join();
        fixture.awaitText(rendered);
        assertThat(fixture.visibleText()).contains("我的学籍档案", "张三", "213240001", "09024101", "正常",
                "本科生", "2024-09-01", "major-1", "class-1", "zhangsan@seu.edu.cn", "13800000000");
        assertThat(fixture.button("student.profile.edit").isEnabled()).isTrue();
        assertThat(updatedOnEdt).isTrue();
    }

    @Test void nullContactValuesRenderAsNotFilled() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);
        fixture.awaitDispatch(1); var rendered = onEdt(() -> fixture.observeText("student.profile.name"));
        response.complete(ResponseBody.success(profileWithNulls())); fixture.awaitText(rendered);
        assertThat(fixture.label("student.profile.name").getText()).isEqualTo("张三");
        assertThat(fixture.label("student.profile.email").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.phone").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.type").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.lifecycle").getText()).isEqualTo("未填写");
        assertThat(fixture.label("student.profile.enrollment").getText()).isEqualTo("未填写");
    }

    @Test void profileActionsAreAccessibleAndRemainInsideSupportedContentSizes() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);

        for (int[] size : new int[][]{{1280, 800}, {1024, 680}}) {
            SwingUtilities.invokeAndWait(() -> layout(fixture.panel, size[0], size[1]));
            for (String name : new String[]{"student.profile.refresh", "student.profile.edit"}) {
                JButton action = fixture.button(name);
                assertThat(action.getAccessibleContext().getAccessibleName()).isNotBlank();
                assertThat(boundsIn(fixture.panel, action).width).isPositive();
                assertThat(boundsIn(fixture.panel, action).height).isPositive();
                assertThat(fixture.panel.getBounds().contains(boundsIn(fixture.panel, action))).isTrue();
            }
        }

        assertThat(fixture.panel.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
    }

    @Test void lifecycleStatusesRenderChineseLabels() throws Exception {
        for (var status : new StudentStatus[]{StudentStatus.SUSPENDED, StudentStatus.GRADUATED, StudentStatus.WITHDRAWN}) {
            var response = new CompletableFuture<ResponseBody<StudentView>>();
            var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
            SwingUtilities.invokeAndWait(fixture::showProfile);
            fixture.awaitDispatch(1);
            var rendered = onEdt(() -> fixture.observeText("student.profile.lifecycle"));
            response.complete(ResponseBody.success(profile(status))); fixture.awaitText(rendered);
            assertThat(fixture.visibleText()).contains(status == StudentStatus.SUSPENDED ? "休学" : status == StudentStatus.GRADUATED ? "已毕业" : "已退学");
        }
    }

    @Test void refreshKeepsOldValuesWhileLoading() throws Exception {
        var first = new CompletableFuture<ResponseBody<StudentView>>();
        var second = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(first, second, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.awaitDispatch(1);
        var initial = onEdt(() -> fixture.observeText("student.profile.email"));
        first.complete(ResponseBody.success(profile(1, "old@seu.edu.cn", "1"))); fixture.awaitText(initial);
        SwingUtilities.invokeAndWait(() -> fixture.panel.refreshProfile());
        fixture.awaitDispatch(2);
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
        var current = onEdt(() -> fixture.observeText("student.profile.email"));
        second.complete(ResponseBody.success(profile(2, "new@seu.edu.cn", "new-phone"))); fixture.awaitText(current);
        var stale = onEdt(() -> fixture.observeText("student.profile.email"));
        fixture.awaitServiceDependent(first);
        first.complete(ResponseBody.success(profile(1, "stale@seu.edu.cn", "stale-phone"))); fixture.flushEdt(); fixture.assertNoText(stale);
        assertThat(fixture.visibleText()).contains("new@seu.edu.cn", "new-phone").doesNotContain("stale@");
    }

    @Test void failedFutureUsesSafeGenericMessage() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.awaitDispatch(1);
        var failure = onEdt(() -> fixture.observeText("student.profile.error"));
        response.completeExceptionally(new IllegalStateException("secret")); fixture.awaitText(failure);
        assertThat(fixture.visibleText()).contains("档案加载失败，请稍后重试").doesNotContain("secret");
    }

    @Test void failedBodyShowsBusinessMessageAndRetry() throws Exception {
        var initial = new CompletableFuture<ResponseBody<StudentView>>();
        var failed = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(initial, failed, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.awaitDispatch(1);
        var initialRender = onEdt(() -> fixture.observeText("student.profile.email"));
        initial.complete(ResponseBody.success(profile(1, "loaded@seu.edu.cn", "phone"))); fixture.awaitText(initialRender);
        assertThat(fixture.label("student.profile.email").getText()).isEqualTo("loaded@seu.edu.cn");
        assertThat(fixture.button("student.profile.refresh").getText()).isEqualTo("刷新");
        SwingUtilities.invokeAndWait(() -> fixture.panel.refreshProfile()); fixture.awaitDispatch(2);
        var failure = onEdt(() -> fixture.observeText("student.profile.error"));
        failed.complete(ResponseBody.failure("DENIED", "没有权限", null)); fixture.awaitText(failure);
        assertThat(fixture.visibleText()).contains("没有权限");
        assertThat(fixture.button("student.profile.refresh").getText()).isEqualTo("重试");
        assertThat(fixture.button("student.profile.refresh").isEnabled()).isTrue();
    }

    @Test void disconnectedStateRetainsLoadedValuesAndDisablesEdit() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile); fixture.awaitDispatch(1);
        var initial = onEdt(() -> fixture.observeText("student.profile.email"));
        response.complete(ResponseBody.success(profile(1, "x@y", "2"))); fixture.awaitText(initial);
        fixture.connection.close(); fixture.flushEdt();
        assertThat(fixture.visibleText()).contains("x@y");
        assertThat(fixture.button("student.profile.edit").isEnabled()).isFalse();
    }

    @Test void lateResponseAfterRemoveNotifyDoesNotChangeLabels() throws Exception {
        var response = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(response, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showProfile);
        fixture.awaitDispatch(1);
        SwingUtilities.invokeAndWait(fixture.panel::removeNotify);
        var late = onEdt(() -> fixture.observeText("student.profile.email"));
        fixture.awaitServiceDependent(response);
        response.complete(ResponseBody.success(profile(9, "late", "late"))); fixture.flushEdt(); fixture.assertNoText(late);
        assertThat(fixture.visibleText()).doesNotContain("late");
    }

    @Test void editContactDialogUsesFrameOwnerAndImmediatelyRendersReturnedProfile() throws Exception {
        var initial = new CompletableFuture<ResponseBody<StudentView>>();
        var saved = new CompletableFuture<ResponseBody<StudentView>>();
        var fixture = new StudentUiFixture(initial, saved, ConnectionState.CONNECTED);
        onEdt(fixture::showProfileInFrame);
        fixture.awaitDispatch(1);
        var initialRender = onEdt(() -> fixture.observeText("student.profile.email"));
        initial.complete(ResponseBody.success(profile(7, "old@seu.edu.cn", "13000000000")));
        fixture.awaitText(initialRender);

        SwingUtilities.invokeLater(() -> fixture.button("student.profile.edit").doClick());
        UpdateContactDialog dialog = onEdt(() -> Arrays.stream(Window.getWindows())
                .filter(UpdateContactDialog.class::isInstance).map(UpdateContactDialog.class::cast)
                .filter(Window::isShowing).findFirst().orElseThrow());
        assertThat(dialog.getOwner()).isSameAs(fixture.frame);
        onEdt(() -> {
            fixture.component(dialog, "student.contact.email", JTextField.class).setText("new@seu.edu.cn");
            fixture.component(dialog, "student.contact.phone", JTextField.class).setText("13800000000");
            fixture.button(dialog, "student.contact.submit").doClick();
        });
        fixture.awaitDispatch(2);
        var savedRender = onEdt(() -> fixture.observeText("student.profile.email"));
        saved.complete(ResponseBody.success(profile(8, "new@seu.edu.cn", "13800000000")));
        fixture.awaitText(savedRender);

        assertThat(fixture.label("student.profile.email").getText()).isEqualTo("new@seu.edu.cn");
        assertThat(fixture.label("student.profile.phone").getText()).isEqualTo("13800000000");
        assertThat(fixture.label("student.profile.version").getText()).isEqualTo("8");
    }

    private static StudentView profile(long version, String email, String phone) {
        return new StudentView("student-1", "user-1", "213240001", "09024101", StudentType.UNDERGRADUATE,
                "张三", "MALE", email, phone, "major-1", "class-1", LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, version);
    }
    private static StudentView profile(StudentStatus status) { var p = profile(1, "a", "b"); return new StudentView(p.studentId(), p.userId(), p.campusCardNumber(), p.studentNumber(), p.studentType(), p.studentName(), p.gender(), p.email(), p.phone(), p.majorId(), p.classId(), p.enrollmentDate(), status, p.rowVersion()); }
    private static StudentView profileWithNulls() { var p = profile(1, null, null); return new StudentView(p.studentId(), p.userId(), p.campusCardNumber(), p.studentNumber(), null, p.studentName(), p.gender(), null, null, p.majorId(), p.classId(), null, null, p.rowVersion()); }
    private static <T> T onEdt(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>(); var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Throwable thrown) { failure.set(thrown); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }
    private static void onEdt(ThrowingRunnable work) throws Exception { onEdt(() -> { work.run(); return null; }); }
    private static void layout(Container root, int width, int height) {
        root.setBounds(0, 0, width, height);
        layoutTree(root);
    }
    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }
    private static Rectangle boundsIn(Container root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }

    private static final class StudentUiFixture {
        final ClientConnection connection = new ClientConnection("localhost", 1); final StudentClientService students;
        final CompletableFuture<ResponseBody<StudentView>>[] responses;
        final AtomicInteger sendCount = new AtomicInteger();
        final AtomicInteger responseIndex = new AtomicInteger();
        final CountDownLatch[] dispatches = {new CountDownLatch(1), new CountDownLatch(1)};
        MyStudentProfilePanel panel;
        JFrame frame;
        StudentUiFixture(CompletableFuture<ResponseBody<StudentView>> response, ConnectionState state) { this(response, new CompletableFuture[0]); setState(state); }
        StudentUiFixture(CompletableFuture<ResponseBody<StudentView>> response, CompletableFuture<ResponseBody<StudentView>> next, ConnectionState state) { this(response, next); setState(state); }
        @SafeVarargs StudentUiFixture(CompletableFuture<ResponseBody<StudentView>> response, CompletableFuture<ResponseBody<StudentView>>... next) {
            connections.add(connection);
            this.responses = new CompletableFuture[next.length + 1]; this.responses[0] = response; System.arraycopy(next, 0, responses, 1, next.length);
            StudentRequestClient client = new StudentRequestClient() {
                @SuppressWarnings("unchecked") public <T extends java.io.Serializable> CompletableFuture<ResponseBody<T>> send(String command, java.io.Serializable body, Duration timeout) { var selected = responses[Math.min(responseIndex.getAndIncrement(), responses.length - 1)]; int call = sendCount.incrementAndGet(); if (call <= dispatches.length) dispatches[call - 1].countDown(); return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) selected; }
            }; students = new StudentClientService(client, Duration.ofSeconds(1));
        }
        void setState(ConnectionState state) { try { var f = ClientConnection.class.getDeclaredField("state"); f.setAccessible(true); f.set(connection, state); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
        void awaitDispatch(int count) throws InterruptedException { assertThat(count).isBetween(1, dispatches.length); assertThat(dispatches[count - 1].await(2, TimeUnit.SECONDS)).isTrue(); }
        void awaitServiceDependent(CompletableFuture<?> response) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (response.getNumberOfDependents() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(response.getNumberOfDependents()).isGreaterThan(0);
        }
        void showProfile() { panel = new MyStudentProfilePanel(students, connection); panel.addNotify(); }
        void showProfileInFrame() { panel = new MyStudentProfilePanel(students, connection); frame = new JFrame("profile"); frame.setContentPane(panel); frame.pack(); frame.setVisible(true); }
        void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
        TextSignal observeText(String name) {
            var signal = new TextSignal();
            label(name).addPropertyChangeListener("text", event -> {
                signal.onEdt.set(SwingUtilities.isEventDispatchThread());
                signal.changed.countDown();
            });
            return signal;
        }
        void awaitText(TextSignal signal) throws InterruptedException {
            assertThat(signal.changed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(signal.onEdt).isTrue();
        }
        void assertNoText(TextSignal signal) throws InterruptedException {
            assertThat(signal.changed.await(2, TimeUnit.SECONDS)).isFalse();
        }
        String visibleText() { return text(panel); }
        <T extends Component> T component(String name, Class<T> type) { return type.cast(find(panel, name)); }
        <T extends Component> T component(Container root, String name, Class<T> type) { return type.cast(find(root, name)); }
        JButton button(String name) { return component(name, JButton.class); }
        JButton button(Container root, String name) { return component(root, name, JButton.class); }
        JLabel label(String name) { return component(name, JLabel.class); }
        static String text(Component c) { if (c instanceof JLabel l) return l.getText() + " " + l.getName(); if (c instanceof AbstractButton b) return b.getText(); if (c instanceof Container p) { var s = new StringBuilder(); for (var x : p.getComponents()) s.append(text(x)).append(' '); return s.toString(); } return ""; }
        static Component find(Container p, String n) { if (n.equals(p.getName())) return p; for (var c : p.getComponents()) { if (n.equals(c.getName())) return c; if (c instanceof Container q) { var x = find(q, n); if (x != null) return x; } } return null; }
        private static final class TextSignal {
            final CountDownLatch changed = new CountDownLatch(1);
            final AtomicBoolean onEdt = new AtomicBoolean();
        }
    }
}
