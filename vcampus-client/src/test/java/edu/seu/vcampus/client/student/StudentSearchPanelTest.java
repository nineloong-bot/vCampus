package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.StudentSearchPanel;
import edu.seu.vcampus.common.paging.PageResult;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StudentSearchPanelTest {
    private static final java.util.List<ClientConnection> connections = new java.util.ArrayList<>();

    @AfterEach void closeConnections() throws Exception {
        connections.forEach(ClientConnection::close);
        connections.clear();
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test void searchResultsRenderIntoTable() throws Exception {
        var client = new AutoCompletingClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        client.enqueue(ResponseBody.success(new PageResult<>(
                List.of(new StudentSummary("student-1", "09024101", "213240001", "张三", "m1", "c1", StudentStatus.ACTIVE),
                        new StudentSummary("student-2", "09024102", "213240002", "李四", "m2", "c2", StudentStatus.GRADUATED)),
                1, 20, 2)));

        var panel = showPanel(client);
        flushEdt();

        JTable table = find(panel, "student.search.table");
        JScrollPane scroll = find(panel, "student.search.table.scroll");
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(scroll.isVisible()).isTrue();
        assertThat(table.getValueAt(0, 0)).isEqualTo("09024101");
        assertThat(table.getValueAt(0, 1)).isEqualTo("213240001");
        assertThat(table.getValueAt(0, 2)).isEqualTo("张三");
        assertThat(table.getValueAt(0, 3)).isEqualTo("正常");
        assertThat(table.getValueAt(1, 2)).isEqualTo("李四");
        assertThat(table.getValueAt(1, 3)).isEqualTo("已毕业");
    }

    @Test void filtersStayOnTwoAlignedRowsAtMinimumWindowWidth() throws Exception {
        var client = new AutoCompletingClient();
        var panel = createPanel(client);

        onEdt(() -> {
            panel.setBounds(0, 0, 1024, 568);
            layoutTree(panel);
            JPanel filters = find(panel, "student.search.filters");
            JTextField keyword = find(panel, "student.search.keyword");
            JComboBox<?> department = find(panel, "student.search.department");
            JComboBox<?> major = find(panel, "student.search.major");
            JComboBox<?> studentClass = find(panel, "student.search.class");
            JComboBox<?> status = find(panel, "student.search.status", JComboBox.class);
            JButton submit = find(panel, "student.search.submit");

            Rectangle keywordBounds = boundsIn(filters, keyword);
            Rectangle departmentBounds = boundsIn(filters, department);
            Rectangle majorBounds = boundsIn(filters, major);
            Rectangle classBounds = boundsIn(filters, studentClass);
            Rectangle statusBounds = boundsIn(filters, status);
            Rectangle submitBounds = boundsIn(filters, submit);
            assertThat(departmentBounds.y).isGreaterThan(keywordBounds.y);
            assertThat(departmentBounds.y).isEqualTo(majorBounds.y).isEqualTo(classBounds.y);
            assertThat(departmentBounds.width).isEqualTo(majorBounds.width).isEqualTo(classBounds.width);
            assertThat(keywordBounds.y).isEqualTo(statusBounds.y).isEqualTo(submitBounds.y);
            assertThat(submitBounds.getMaxX()).isLessThanOrEqualTo(filters.getWidth());
            return null;
        });
    }

    @Test void cascadingDropdownsClearChildren() throws Exception {
        var client = new AutoCompletingClient();
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new DepartmentView("d1", "01", "计算机", true, 1),
                new DepartmentView("d2", "02", "数学", true, 1)))));
        client.enqueue(ResponseBody.success(new PageResult<>(new ArrayList<>(), 1, 20, 0)));
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new MajorView("m1", "d1", "0101", "计科", true, 1)))));
        client.enqueue(ResponseBody.success(new PageResult<>(new ArrayList<>(), 1, 20, 0)));
        client.enqueue(ResponseBody.success(new ArrayList<>(List.of(
                new ClassView("c1", "m1", "010101", "计科1班", 2024, 1, true, 1)))));
        client.enqueue(ResponseBody.success(new PageResult<>(new ArrayList<>(), 1, 20, 0)));

        var panel = showPanel(client);
        flushEdt();

        JComboBox<?> deptCombo = find(panel, "student.search.department");
        assertThat(deptCombo.getItemCount()).isEqualTo(3);

        deptCombo.setSelectedIndex(1);
        flushEdt();

        JComboBox<?> majorCombo = find(panel, "student.search.major");
        assertThat(majorCombo.getItemCount()).isEqualTo(2);

        majorCombo.setSelectedIndex(1);
        flushEdt();

        JComboBox<?> classCombo = find(panel, "student.search.class");
        assertThat(classCombo.getItemCount()).isEqualTo(2);
    }

    @Test void emptyResultsShowMessage() throws Exception {
        var client = new AutoCompletingClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        client.enqueue(ResponseBody.success(new PageResult<>(new ArrayList<>(), 1, 20, 0)));

        var panel = showPanel(client);
        flushEdt();

        JLabel emptyLabel = find(panel, "student.search.empty");
        assertThat(emptyLabel.isVisible()).isTrue();
        assertThat(emptyLabel.getText()).contains("未找到");
    }

    @Test void paginationControlsWork() throws Exception {
        var client = new AutoCompletingClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        client.enqueue(ResponseBody.success(new PageResult<>(
                List.of(new StudentSummary("s1", "c1", "n1", "张三", "m1", "cl1", StudentStatus.ACTIVE)),
                1, 20, 25)));
        client.enqueue(ResponseBody.success(new PageResult<>(
                List.of(new StudentSummary("s2", "c2", "n2", "李四", "m2", "cl2", StudentStatus.ACTIVE)),
                2, 20, 25)));

        var panel = showPanel(client);
        waitForButtonEnabled(panel, "student.search.next");

        JButton prev = find(panel, "student.search.prev");
        JButton next = find(panel, "student.search.next");
        JLabel page = find(panel, "student.search.page");
        assertThat(prev.isEnabled()).isFalse();
        assertThat(next.isEnabled()).isTrue();
        assertThat(page.getText()).isEqualTo("第1页/共25条");

        next.doClick();
        waitForButtonEnabled(panel, "student.search.prev");

        assertThat(prev.isEnabled()).isTrue();
        assertThat(next.isEnabled()).isFalse();
        assertThat(page.getText()).isEqualTo("第2页/共25条");
    }

    @Test void disconnectedStateDisablesSearch() throws Exception {
        var client = new AutoCompletingClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        client.enqueue(ResponseBody.success(new PageResult<>(new ArrayList<>(), 1, 20, 0)));

        var panel = showPanel(client);
        flushEdt();

        lastConnection.close();
        flushEdt();

        assertThat(((JButton) find(panel, "student.search.submit")).isEnabled()).isFalse();
    }

    @Test void staleResponseIsDiscarded() throws Exception {
        var client = new AutoCompletingClient();
        client.enqueue(ResponseBody.success(new ArrayList<>()));
        // Don't enqueue search response yet — only departments response is available

        var panel = showPanel(client);
        flushEdt(); // departments load, search supplyAsync runs but send() returns null (no response in queue)

        panel.removeNotify(); // increments generation
        flushEdt();

        // Now enqueue the search response — too late, the supplyAsync already returned a never-completing future
        client.enqueue(ResponseBody.success(new PageResult<>(
                List.of(new StudentSummary("s1", "c1", "n1", "张三", "m1", "cl1", StudentStatus.ACTIVE)),
                1, 20, 1)));
        flushEdt();

        JTable table = find(panel, "student.search.table");
        assertThat(table.getRowCount()).isEqualTo(0);
    }

    private ClientConnection lastConnection;

    private StudentSearchPanel showPanel(AutoCompletingClient client) throws Exception {
        StudentSearchPanel panel = createPanel(client);
        onEdt(() -> { panel.addNotify(); return null; });
        return panel;
    }

    private StudentSearchPanel createPanel(AutoCompletingClient client) throws Exception {
        var service = new StudentClientService(client, Duration.ofSeconds(3));
        var connection = new ClientConnection("localhost", 1);
        connections.add(connection);
        lastConnection = connection;
        try {
            var f = ClientConnection.class.getDeclaredField("state");
            f.setAccessible(true);
            f.set(connection, edu.seu.vcampus.client.core.network.ConnectionState.CONNECTED);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        return onEdt(() -> new StudentSearchPanel(service, connection, id -> {}));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(Container root, String name) {
        if (name.equals(root.getName()) && root instanceof Component) return (T) root;
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return (T) child;
            if (child instanceof Container nested) {
                try { return find(nested, name); } catch (IllegalArgumentException ignored) { }
            }
        }
        throw new IllegalArgumentException("Missing component: " + name);
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, name, type); if (found != null) return found;
            }
        }
        throw new IllegalArgumentException("Missing component: " + name + " of type " + type.getSimpleName());
    }

    private static <T extends Component> T findOrNull(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName()) && type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, name, type); if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T onEdt(Callable<T> work) throws Exception {
        var result = new AtomicReference<T>();
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container nested) layoutTree(nested);
    }

    private static Rectangle boundsIn(Container root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    private static void waitForTableRows(JPanel panel, int expectedRows) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            flushEdt();
            int[] count = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                JTable t = find(panel, "student.search.table");
                count[0] = t.getRowCount();
            });
            if (count[0] >= expectedRows) return;
        }
        throw new AssertionError("Table did not reach " + expectedRows + " rows within timeout");
    }

    private static void waitForButtonEnabled(JPanel panel, String buttonName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            flushEdt();
            boolean[] enabled = new boolean[1];
            SwingUtilities.invokeAndWait(() -> enabled[0] = ((JButton) find(panel, buttonName)).isEnabled());
            if (enabled[0]) return;
        }
        throw new AssertionError("Button " + buttonName + " not enabled within timeout");
    }

    private static final class AutoCompletingClient implements StudentRequestClient {
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
