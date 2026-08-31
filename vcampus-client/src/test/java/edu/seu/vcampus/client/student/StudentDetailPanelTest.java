package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.StudentDetailPanel;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class StudentDetailPanelTest {
    private static final java.util.List<ClientConnection> connections = new java.util.ArrayList<>();

    @AfterEach
    void closeConnections() throws Exception {
        connections.forEach(ClientConnection::close);
        connections.clear();
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void profileFieldsRenderOnEdt() throws Exception {
        var getResponse = new CompletableFuture<ResponseBody<StudentView>>();
        var changesResponse = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        var fixture = new DetailFixture(getResponse, changesResponse, ConnectionState.CONNECTED, true);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        assertThat(fixture.label("student.detail.status").getText()).contains("正在加载");
        assertThat(fixture.button("student.detail.edit-contact").isEnabled()).isFalse();
        var updatedOnEdt = new AtomicBoolean();
        fixture.label("student.detail.name").addPropertyChangeListener("text", e -> updatedOnEdt.set(SwingUtilities.isEventDispatchThread()));
        fixture.awaitDispatch(1);
        var rendered = onEdt(() -> fixture.observeText("student.detail.name"));
        getResponse.complete(ResponseBody.success(profile()));
        fixture.awaitDispatch(2);
        changesResponse.complete(ResponseBody.success(new ArrayList<>()));
        fixture.awaitText(rendered);
        assertThat(fixture.visibleText()).contains("学生详情", "张三", "男", "本科生",
                "213240001", "09024101", "class-1", "2024-09-01", "正常",
                "zhangsan@seu.edu.cn", "13800000000", "7");
        assertThat(fixture.button("student.detail.edit-contact").isEnabled()).isTrue();
        assertThat(updatedOnEdt).isTrue();
    }

    @Test
    void numberBreakdownShowsDigitMeanings() throws Exception {
        var getResponse = new CompletableFuture<ResponseBody<StudentView>>();
        var changesResponse = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        var fixture = new DetailFixture(getResponse, changesResponse, ConnectionState.CONNECTED, true);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.awaitDispatch(1);
        var rendered = onEdt(() -> fixture.observeText("student.detail.card.breakdown"));
        getResponse.complete(ResponseBody.success(profile()));
        fixture.awaitDispatch(2);
        changesResponse.complete(ResponseBody.success(new ArrayList<>()));
        fixture.awaitText(rendered);
        assertThat(fixture.label("student.detail.card.breakdown").getText())
                .contains("类型:本科", "入学年:2024", "序号:0001");
        assertThat(fixture.label("student.detail.studentNumber.breakdown").getText())
                .contains("专业:090", "入学年:2024", "班级:1", "班内序号:01");
    }

    @Test
    void changeHistoryRendersIntoTable() throws Exception {
        var getResponse = new CompletableFuture<ResponseBody<StudentView>>();
        var changesResponse = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        var fixture = new DetailFixture(getResponse, changesResponse, ConnectionState.CONNECTED, true);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.awaitDispatch(1);
        getResponse.complete(ResponseBody.success(profile()));
        fixture.awaitDispatch(2);
        var changes = new ArrayList<StudentChangeView>();
        changes.add(new StudentChangeView("c1", "student-1", "ADMISSION", "", "录取",
                "高考录取", "admin-1", LocalDate.of(2024, 8, 1), Instant.parse("2024-08-01T10:00:00Z")));
        changes.add(new StudentChangeView("c2", "student-1", "STATUS_CHANGE", "正常", "休学",
                "个人原因", "admin-2", LocalDate.of(2025, 3, 1), Instant.parse("2025-03-01T10:00:00Z")));
        var changesRendered = onEdt(() -> fixture.observeTable("student.detail.changes"));
        changesResponse.complete(ResponseBody.success(changes));
        fixture.awaitTable(changesRendered);
        JTable table = fixture.table("student.detail.changes");
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(table.getValueAt(0, 0)).isEqualTo("录取");
        assertThat(table.getValueAt(0, 3)).isEqualTo("高考录取");
        assertThat(table.getValueAt(1, 0)).isEqualTo("状态变更");
        assertThat(table.getValueAt(1, 1)).isEqualTo("正常");
        assertThat(table.getValueAt(1, 2)).isEqualTo("休学");
    }

    @Test
    void canEditFalseDisablesActionButtons() throws Exception {
        var getResponse = new CompletableFuture<ResponseBody<StudentView>>();
        var changesResponse = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        var fixture = new DetailFixture(getResponse, changesResponse, ConnectionState.CONNECTED, false);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.awaitDispatch(1);
        var rendered = onEdt(() -> fixture.observeText("student.detail.name"));
        getResponse.complete(ResponseBody.success(profile()));
        fixture.awaitDispatch(2);
        changesResponse.complete(ResponseBody.success(new ArrayList<>()));
        fixture.awaitText(rendered);
        assertThat(fixture.button("student.detail.edit-contact").isVisible()).isFalse();
        assertThat(fixture.button("student.detail.change-status").isVisible()).isFalse();
        assertThat(fixture.button("student.detail.transfer").isVisible()).isFalse();
    }

    @Test
    void disconnectedStateDisablesActions() throws Exception {
        var getResponse = new CompletableFuture<ResponseBody<StudentView>>();
        var changesResponse = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        var fixture = new DetailFixture(getResponse, changesResponse, ConnectionState.CONNECTED, true);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.awaitDispatch(1);
        var rendered = onEdt(() -> fixture.observeText("student.detail.name"));
        getResponse.complete(ResponseBody.success(profile()));
        fixture.awaitDispatch(2);
        changesResponse.complete(ResponseBody.success(new ArrayList<>()));
        fixture.awaitText(rendered);
        assertThat(fixture.button("student.detail.edit-contact").isEnabled()).isTrue();
        fixture.connection.close();
        fixture.flushEdt();
        assertThat(fixture.button("student.detail.edit-contact").isEnabled()).isFalse();
        assertThat(fixture.button("student.detail.change-status").isEnabled()).isFalse();
        assertThat(fixture.button("student.detail.transfer").isEnabled()).isFalse();
        assertThat(fixture.visibleText()).contains("zhangsan@seu.edu.cn");
    }

    @Test
    void staleResponseAfterRemoveNotifyIsDiscarded() throws Exception {
        var getResponse = new CompletableFuture<ResponseBody<StudentView>>();
        var changesResponse = new CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>();
        var fixture = new DetailFixture(getResponse, changesResponse, ConnectionState.CONNECTED, true);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.awaitDispatch(1);
        SwingUtilities.invokeAndWait(fixture.panel::removeNotify);
        var late = onEdt(() -> fixture.observeText("student.detail.name"));
        getResponse.complete(ResponseBody.success(profile(9, "late", "late")));
        changesResponse.complete(ResponseBody.success(new ArrayList<>()));
        fixture.flushEdt();
        fixture.assertNoText(late);
        assertThat(fixture.visibleText()).doesNotContain("late");
    }

    private static StudentView profile() {
        return profile(7, "zhangsan@seu.edu.cn", "13800000000");
    }

    private static StudentView profile(long version, String email, String phone) {
        return new StudentView("student-1", "user-1", "213240001", "09024101", StudentType.UNDERGRADUATE,
                "张三", "MALE", email, phone, "major-1", "class-1",
                LocalDate.of(2024, 9, 1), StudentStatus.ACTIVE, version);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(work.call());
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void onEdt(ThrowingRunnable work) throws Exception {
        onEdt(() -> {
            work.run();
            return null;
        });
    }

    private static void layout(Container root, int width, int height) {
        root.setBounds(0, 0, width, height);
        layoutTree(root);
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private static Rectangle boundsIn(Container root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    private static final class DetailFixture {
        final ClientConnection connection = new ClientConnection("localhost", 1);
        final StudentClientService students;
        final CompletableFuture<ResponseBody<StudentView>>[] getResponses;
        final CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>>[] changesResponses;
        final AtomicInteger dispatchCount = new AtomicInteger();
        final AtomicInteger responseIndex = new AtomicInteger();
        final AtomicInteger changesIndex = new AtomicInteger();
        final boolean canEdit;
        StudentDetailPanel panel;

        @SafeVarargs
        DetailFixture(CompletableFuture<ResponseBody<StudentView>> getResponse,
                       CompletableFuture<ResponseBody<ArrayList<StudentChangeView>>> changesResponse,
                       ConnectionState state, boolean canEdit, CompletableFuture<ResponseBody<StudentView>>... nextGet) {
            this.canEdit = canEdit;
            connections.add(connection);
            this.getResponses = new CompletableFuture[nextGet.length + 1];
            this.getResponses[0] = getResponse;
            System.arraycopy(nextGet, 0, getResponses, 1, nextGet.length);
            this.changesResponses = new CompletableFuture[]{changesResponse};
            setState(state);
            StudentRequestClient client = new StudentRequestClient() {
                @SuppressWarnings("unchecked")
                public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                        String command, Serializable body, Duration timeout) {
                    dispatchCount.incrementAndGet();
                    if ("STUDENT_GET".equals(command)) {
                        var selected = getResponses[Math.min(responseIndex.getAndIncrement(), getResponses.length - 1)];
                        return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) selected;
                    } else if ("STUDENT_GET_CHANGES".equals(command)) {
                        var selected = changesResponses[Math.min(changesIndex.getAndIncrement(), changesResponses.length - 1)];
                        return (CompletableFuture<ResponseBody<T>>) (CompletableFuture<?>) selected;
                    }
                    return CompletableFuture.failedFuture(new UnsupportedOperationException(command));
                }
            };
            students = new StudentClientService(client, Duration.ofSeconds(1));
        }

        void setState(ConnectionState state) {
            try {
                var f = ClientConnection.class.getDeclaredField("state");
                f.setAccessible(true);
                f.set(connection, state);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        void awaitDispatch(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (dispatchCount.get() < count && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(dispatchCount.get()).isGreaterThanOrEqualTo(count);
        }

        void awaitServiceDependent(CompletableFuture<?> response) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (response.getNumberOfDependents() == 0 && System.nanoTime() < deadline) Thread.onSpinWait();
            assertThat(response.getNumberOfDependents()).isGreaterThan(0);
        }

        void showPanel() {
            panel = new StudentDetailPanel(students, connection, "student-1", canEdit);
            panel.addNotify();
        }

        void flushEdt() throws Exception {
            SwingUtilities.invokeAndWait(() -> {});
        }

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

        TableSignal observeTable(String name) {
            var signal = new TableSignal();
            table(name).getModel().addTableModelListener(e -> {
                signal.onEdt.set(SwingUtilities.isEventDispatchThread());
                signal.changed.countDown();
            });
            return signal;
        }

        void awaitTable(TableSignal signal) throws InterruptedException {
            assertThat(signal.changed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(signal.onEdt).isTrue();
        }

        String visibleText() {
            return text(panel);
        }

        <T extends Component> T component(String name, Class<T> type) {
            return type.cast(find(panel, name));
        }

        JButton button(String name) {
            return component(name, JButton.class);
        }

        JLabel label(String name) {
            return component(name, JLabel.class);
        }

        JTable table(String name) {
            return component(name, JTable.class);
        }

        static String text(Component c) {
            if (c instanceof JLabel l) return l.getText() + " " + l.getName();
            if (c instanceof AbstractButton b) return b.getText();
            if (c instanceof Container p) {
                var s = new StringBuilder();
                for (var x : p.getComponents()) s.append(text(x)).append(' ');
                return s.toString();
            }
            return "";
        }

        static Component find(Container p, String n) {
            if (n.equals(p.getName())) return p;
            for (var c : p.getComponents()) {
                if (n.equals(c.getName())) return c;
                if (c instanceof Container q) {
                    var x = find(q, n);
                    if (x != null) return x;
                }
            }
            return null;
        }

        private static final class TextSignal {
            final CountDownLatch changed = new CountDownLatch(1);
            final AtomicBoolean onEdt = new AtomicBoolean();
        }

        private static final class TableSignal {
            final CountDownLatch changed = new CountDownLatch(1);
            final AtomicBoolean onEdt = new AtomicBoolean();
        }
    }
}
