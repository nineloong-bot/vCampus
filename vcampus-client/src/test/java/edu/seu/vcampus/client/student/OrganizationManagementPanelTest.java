package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.network.ConnectionState;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.OrganizationManagementPanel;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationManagementPanelTest {
    private static final java.util.List<ClientConnection> connections = new java.util.ArrayList<>();

    @AfterEach
    void closeConnections() throws Exception {
        connections.forEach(ClientConnection::close);
        connections.clear();
        SwingUtilities.invokeAndWait(() -> Arrays.stream(Window.getWindows()).forEach(Window::dispose));
    }

    @Test
    void treeRendersHierarchy() throws Exception {
        var client = new HierarchyClient();

        var fixture = new OrgFixture(client, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.waitForTreeLoaded(2);
        fixture.waitForFirstClassLoaded();

        JTree tree = fixture.component("student.org.tree", JTree.class);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        assertThat(root.getChildCount()).isEqualTo(2);

        DefaultMutableTreeNode dept0 = (DefaultMutableTreeNode) root.getChildAt(0);
        assertThat(textOf(dept0)).contains("CS", "计算机学院");
        assertThat(dept0.getChildCount()).isEqualTo(1);

        DefaultMutableTreeNode major0 = (DefaultMutableTreeNode) dept0.getChildAt(0);
        assertThat(textOf(major0)).contains("CS01", "软件工程");
        assertThat(major0.getChildCount()).isEqualTo(1);

        DefaultMutableTreeNode class0 = (DefaultMutableTreeNode) major0.getChildAt(0);
        assertThat(textOf(class0)).contains("01", "一班");

        DefaultMutableTreeNode dept1 = (DefaultMutableTreeNode) root.getChildAt(1);
        assertThat(textOf(dept1)).contains("EE", "电子工程学院");
        assertThat(dept1.getChildCount()).isZero();
    }

    @Test
    void selectingNodeSwitchesEditForm() throws Exception {
        var client = new HierarchyClient();

        var fixture = new OrgFixture(client, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.waitForTreeLoaded(2);
        fixture.waitForFirstClassLoaded();

        JTree tree = fixture.component("student.org.tree", JTree.class);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode deptNode = (DefaultMutableTreeNode) root.getChildAt(0);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(deptNode.getPath())));
        flushEdt();
        assertThat(componentExists(fixture.panel, "student.org.code")).isTrue();
        assertThat(componentExists(fixture.panel, "student.org.name")).isTrue();
        assertThat(componentExists(fixture.panel, "student.org.active")).isTrue();
        assertThat(componentExists(fixture.panel, "student.org.parent")).isFalse();

        DefaultMutableTreeNode classNode = (DefaultMutableTreeNode) ((DefaultMutableTreeNode) deptNode.getChildAt(0)).getChildAt(0);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(classNode.getPath())));
        flushEdt();
        assertThat(componentExists(fixture.panel, "student.org.year")).isTrue();
        assertThat(componentExists(fixture.panel, "student.org.number")).isTrue();
        assertThat(componentExists(fixture.panel, "student.org.parent")).isTrue();
    }

    @Test
    void onlyTheActionForTheSelectedHierarchyLevelIsEnabled() throws Exception {
        var fixture = new OrgFixture(new HierarchyClient(), ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.waitForTreeLoaded(2);
        fixture.waitForFirstClassLoaded();

        JButton department = fixture.button("student.org.add-dept");
        JButton major = fixture.button("student.org.add-major");
        JButton studentClass = fixture.button("student.org.add-class");
        JButton student = fixture.button("student.org.add-student");
        assertThat(java.util.List.of(department, major, studentClass, student))
                .allMatch(Component::isVisible);
        assertThat(java.util.List.of(department.isEnabled(), major.isEnabled(),
                studentClass.isEnabled(), student.isEnabled()))
                .containsExactly(true, false, false, false);

        JTree tree = fixture.component("student.org.tree", JTree.class);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode departmentNode = (DefaultMutableTreeNode) root.getChildAt(0);
        DefaultMutableTreeNode majorNode = (DefaultMutableTreeNode) departmentNode.getChildAt(0);
        DefaultMutableTreeNode classNode = (DefaultMutableTreeNode) majorNode.getChildAt(0);

        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(departmentNode.getPath())));
        assertThat(java.util.List.of(department.isEnabled(), major.isEnabled(),
                studentClass.isEnabled(), student.isEnabled()))
                .containsExactly(false, true, false, false);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(majorNode.getPath())));
        assertThat(java.util.List.of(department.isEnabled(), major.isEnabled(),
                studentClass.isEnabled(), student.isEnabled()))
                .containsExactly(false, false, true, false);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(classNode.getPath())));
        assertThat(java.util.List.of(department.isEnabled(), major.isEnabled(),
                studentClass.isEnabled(), student.isEnabled()))
                .containsExactly(false, false, false, true);
    }

    @Test
    void saveNewDepartment() throws Exception {
        var client = new HierarchyClient(new ArrayList<>());

        var fixture = new OrgFixture(client, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        flushEdt();

        SwingUtilities.invokeAndWait(() -> fixture.button("student.org.add-dept").doClick());
        flushEdt();
        assertThat(componentExists(fixture.panel, "student.org.code")).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            fixture.field("student.org.code").setText("PHY");
            fixture.field("student.org.name").setText("物理学院");
        });

        client.enqueueWrite(ResponseBody.success(new DepartmentView("dept-new", "PHY", "物理学院", true, 1)));
        SwingUtilities.invokeAndWait(() -> fixture.button("student.org.save").doClick());
        flushEdt();
    }

    @Test
    void updateExistingDepartment() throws Exception {
        var client = new HierarchyClient();

        var fixture = new OrgFixture(client, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.waitForTreeLoaded(2);

        JTree tree = fixture.component("student.org.tree", JTree.class);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode deptNode = (DefaultMutableTreeNode) root.getChildAt(0);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(deptNode.getPath())));
        flushEdt();

        SwingUtilities.invokeAndWait(() -> fixture.field("student.org.name").setText("计算机科学与技术学院"));

        client.enqueueWrite(ResponseBody.success(new DepartmentView("dept-1", "CS", "计算机科学与技术学院", true, 6)));
        SwingUtilities.invokeAndWait(() -> fixture.button("student.org.save").doClick());
        flushEdt();
    }

    @Test
    void addMajorUnderDepartment() throws Exception {
        var client = new HierarchyClient();

        var fixture = new OrgFixture(client, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.waitForTreeLoaded(2);

        JTree tree = fixture.component("student.org.tree", JTree.class);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode deptNode = (DefaultMutableTreeNode) root.getChildAt(0);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(deptNode.getPath())));
        flushEdt();
        assertThat(fixture.button("student.org.add-major").isVisible()).isTrue();

        SwingUtilities.invokeAndWait(() -> fixture.button("student.org.add-major").doClick());
        flushEdt();
        assertThat(componentExists(fixture.panel, "student.org.parent")).isTrue();
        JLabel parentLabel = fixture.component("student.org.parent", JLabel.class);
        assertThat(parentLabel.getText()).contains("CS", "计算机学院");
        assertThat(componentExists(fixture.panel, "student.org.code")).isTrue();
        assertThat(componentExists(fixture.panel, "student.org.save")).isTrue();
    }

    @Test
    void conflictShowsError() throws Exception {
        var client = new HierarchyClient();

        var fixture = new OrgFixture(client, ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(fixture::showPanel);
        fixture.waitForTreeLoaded(2);

        JTree tree = fixture.component("student.org.tree", JTree.class);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        DefaultMutableTreeNode deptNode = (DefaultMutableTreeNode) root.getChildAt(0);
        SwingUtilities.invokeAndWait(() -> tree.setSelectionPath(new TreePath(deptNode.getPath())));
        flushEdt();

        SwingUtilities.invokeAndWait(() -> fixture.field("student.org.name").setText("新名称"));

        client.enqueueWrite(ResponseBody.failure("COMMON_CONCURRENT_MODIFICATION", "数据已被修改", null));
        SwingUtilities.invokeAndWait(() -> fixture.button("student.org.save").doClick());
        fixture.waitForLabelContains("student.org.error", "刷新");
        assertThat(fixture.label("student.org.error").getText()).contains("刷新");
    }

    private static ArrayList<DepartmentView> departments() {
        var list = new ArrayList<DepartmentView>();
        list.add(new DepartmentView("dept-1", "CS", "计算机学院", true, 5));
        list.add(new DepartmentView("dept-2", "EE", "电子工程学院", true, 2));
        return list;
    }

    private static ArrayList<MajorView> majors(String departmentId) {
        var list = new ArrayList<MajorView>();
        if ("dept-1".equals(departmentId)) {
            list.add(new MajorView("major-1", "dept-1", "CS01", "软件工程", true, 3));
        }
        return list;
    }

    private static ArrayList<ClassView> classes(String majorId) {
        var list = new ArrayList<ClassView>();
        if ("major-1".equals(majorId)) {
            list.add(new ClassView("class-1", "major-1", "01", "一班", 2024, 1, true, 1));
        }
        return list;
    }

    private static String textOf(DefaultMutableTreeNode node) {
        Object obj = node.getUserObject();
        if (obj instanceof DepartmentView d) return d.code() + " - " + d.name();
        if (obj instanceof MajorView m) return m.code() + " - " + m.name();
        if (obj instanceof ClassView c) return c.code() + " - " + c.name();
        return obj.toString();
    }

    private static boolean componentExists(Container root, String name) {
        if (name.equals(root.getName())) return true;
        for (Component c : root.getComponents()) {
            if (name.equals(c.getName())) return true;
            if (c instanceof Container nested && componentExists(nested, name)) return true;
        }
        return false;
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

    private static final class OrgFixture {
        final ClientConnection connection;
        final StudentClientService students;
        OrganizationManagementPanel panel;

        OrgFixture(StudentRequestClient client, ConnectionState state) {
            this.connection = new ClientConnection("localhost", 1);
            connections.add(connection);
            this.students = new StudentClientService(client, Duration.ofSeconds(1));
            try {
                var f = ClientConnection.class.getDeclaredField("state");
                f.setAccessible(true);
                f.set(connection, state);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        void showPanel() {
            panel = new OrganizationManagementPanel(students, connection);
            panel.addNotify();
        }

        void waitForTreeLoaded(int expectedChildren) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                flushEdt();
                int[] count = new int[1];
                SwingUtilities.invokeAndWait(() -> {
                    var tree = component("student.org.tree", JTree.class);
                    var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
                    count[0] = root.getChildCount();
                });
                if (count[0] >= expectedChildren) return;
            }
            throw new AssertionError("Tree did not load within timeout");
        }

        void waitForFirstClassLoaded() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                flushEdt();
                boolean[] loaded = new boolean[1];
                SwingUtilities.invokeAndWait(() -> {
                    var tree = component("student.org.tree", JTree.class);
                    var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
                    if (root.getChildCount() == 0) return;
                    var department = (DefaultMutableTreeNode) root.getChildAt(0);
                    if (department.getChildCount() == 0) return;
                    var major = (DefaultMutableTreeNode) department.getChildAt(0);
                    loaded[0] = major.getChildCount() > 0;
                });
                if (loaded[0]) return;
                Thread.sleep(10);
            }
            throw new AssertionError("Class node did not load within timeout");
        }

        void waitForLabelContains(String name, String expected) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                flushEdt();
                String[] value = new String[1];
                SwingUtilities.invokeAndWait(() -> value[0] = label(name).getText());
                if (value[0] != null && value[0].contains(expected)) return;
                Thread.sleep(10);
            }
            throw new AssertionError("Label did not contain expected text within timeout: " + expected);
        }

        <T extends Component> T component(String name, Class<T> type) {
            return type.cast(find(panel, name));
        }

        JButton button(String name) { return component(name, JButton.class); }
        JTextField field(String name) { return component(name, JTextField.class); }
        JLabel label(String name) { return component(name, JLabel.class); }

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
    }

    private static final class HierarchyClient implements StudentRequestClient {
        private final ArrayList<DepartmentView> departmentValues;
        private final BlockingQueue<ResponseBody<?>> writes = new LinkedBlockingQueue<>();

        HierarchyClient() { this(departments()); }
        HierarchyClient(ArrayList<DepartmentView> departmentValues) {
            this.departmentValues = departmentValues;
        }
        void enqueueWrite(ResponseBody<?> response) { writes.add(response); }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            ResponseBody<?> response = switch (command) {
                case "STUDENT_LIST_DEPARTMENTS" -> ResponseBody.success(departmentValues);
                case "STUDENT_LIST_MAJORS" -> {
                    var query = (OrganizationChildrenQuery) body;
                    yield ResponseBody.success(majors(query.parentId()));
                }
                case "STUDENT_LIST_CLASSES" -> {
                    var query = (OrganizationChildrenQuery) body;
                    yield ResponseBody.success(classes(query.parentId()));
                }
                default -> writes.poll();
            };
            return response == null ? new CompletableFuture<>()
                    : CompletableFuture.completedFuture((ResponseBody) response);
        }
    }
}
