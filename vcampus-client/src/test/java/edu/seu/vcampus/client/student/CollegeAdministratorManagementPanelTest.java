package edu.seu.vcampus.client.student;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.client.student.service.StudentRequestClient;
import edu.seu.vcampus.client.student.ui.CollegeAdministratorManagementPanel;
import edu.seu.vcampus.client.core.ui.editor.EditorPlacement;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministrationSnapshot;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;
import edu.seu.vcampus.common.user.AccountStatus;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class CollegeAdministratorManagementPanelTest {
    @Test
    void creationEditorIsCompactAndHiddenUntilRequested() throws Exception {
        RecordingClient client = new RecordingClient();
        CollegeAdministratorManagementPanel[] holder = new CollegeAdministratorManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new CollegeAdministratorManagementPanel(
                new StudentClientService(client, Duration.ofSeconds(1))));

        EmbeddedEditorHost host = find(holder[0], EmbeddedEditorHost.class);
        assertThat(host.isEditorOpen()).isFalse();
        SwingUtilities.invokeAndWait(() -> find(holder[0], "collegeAdminCreateButton").doClick());

        assertThat(host.isEditorOpen()).isTrue();
        assertThat(host.currentPlacement()).isEqualTo(EditorPlacement.BOTTOM);
        assertThat(findNamed(holder[0], "newAdminLoginField")).isInstanceOf(JTextField.class);
    }

    @Test
    void assignmentUsesSelectedUnassignedAdministratorAndDepartment() throws Exception {
        RecordingClient client = new RecordingClient();
        CollegeAdministratorManagementPanel[] holder = new CollegeAdministratorManagementPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new CollegeAdministratorManagementPanel(
                    new StudentClientService(client, Duration.ofSeconds(1)));
            holder[0].addNotify();
        });
        JTable table = find(holder[0], JTable.class);
        waitForRows(table, 1);

        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            find(holder[0], "collegeAdminAssignButton").doClick();
        });
        waitFor(() -> client.commands.contains("STUDENT_COLLEGE_ADMIN_ASSIGN"));

        assertThat(client.commands).contains("STUDENT_COLLEGE_ADMIN_SEARCH",
                "STUDENT_COLLEGE_ADMIN_ASSIGN");
        SwingUtilities.invokeAndWait(holder[0]::removeNotify);
    }

    private static void waitFor(java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void waitForRows(JTable table, int expected) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        int[] rows = new int[1];
        do {
            SwingUtilities.invokeAndWait(() -> rows[0] = table.getRowCount());
            if (rows[0] >= expected) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        assertThat(rows[0]).isGreaterThanOrEqualTo(expected);
    }

    private static JButton find(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton button && name.equals(button.getName())) return button;
            if (component instanceof Container child) {
                JButton found = find(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Component findNamed(Container root, String name) {
        for (Component component : root.getComponents()) {
            if (name.equals(component.getName())) return component;
            if (component instanceof Container child) {
                Component found = findNamed(child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container child) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class RecordingClient implements StudentRequestClient {
        private final List<String> commands = new CopyOnWriteArrayList<>();

        @Override @SuppressWarnings("unchecked")
        public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                String command, Serializable body, Duration timeout) {
            commands.add(command);
            if ("STUDENT_COLLEGE_ADMIN_SEARCH".equals(command)) {
                var snapshot = new StudentCollegeAdministrationSnapshot(List.of(
                        new StudentCollegeAdministratorView("user", "COLLEGE_ADMIN_TEST",
                                AccountStatus.ACTIVE, null, null, null, false, 0)),
                        List.of(new DepartmentView("department", "CS", "计算机学院", true, 0)));
                return CompletableFuture.completedFuture(
                        (ResponseBody<T>) ResponseBody.success(snapshot));
            }
            return CompletableFuture.completedFuture(
                    (ResponseBody<T>) ResponseBody.success(EmptyResponse.INSTANCE));
        }
    }
}
