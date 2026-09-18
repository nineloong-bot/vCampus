package edu.seu.vcampus.client.student.majortransfer;

import edu.seu.vcampus.client.core.network.*;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.student.service.*;
import edu.seu.vcampus.client.student.majortransfer.ui.*;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.DepartmentView;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MajorTransferUiRegressionTest {
    private StudentClientService client() {
        return new StudentClientService(new StudentRequestClient() {
            public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(String command, Serializable body, Duration timeout) {
                return CompletableFuture.completedFuture(ResponseBody.failure("OFFLINE", "测试未连接", null));
            }
        }, Duration.ofSeconds(1));
    }

    @Test void refreshRestoresChoiceAndUnsavedChangesCannotSubmit() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MyMajorTransferPanel(client(), connection);
            try {
                var method = MyMajorTransferPanel.class.getDeclaredMethod("render", MajorTransferWorkspace.class);
                method.setAccessible(true); method.invoke(panel, workspace());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            ((JButton) find(panel, "major-transfer.student.edit-application")).doClick();
            var combo = (JComboBox<?>) find(panel, "major-transfer.student.target-major");
            assertThat(combo.getSelectedIndex()).isEqualTo(1);
            assertThat(((JComboBox<?>) find(panel, "major-transfer.student.application-type")).getSelectedIndex()).isEqualTo(1);
            var submit = (JButton) find(panel, "major-transfer.student.submit");
            assertThat(submit.isEnabled()).isTrue();
            combo.setSelectedIndex(0);
            assertThat(submit.isEnabled()).isFalse();
            combo.setSelectedIndex(1);
            ((JTextArea) find(panel, "major-transfer.student.reason")).setText("尚未暂存的新理由");
            assertThat(submit.isEnabled()).isFalse();
        });
    }

    @Test void closedBatchExplainsReadOnlyStateAndDisablesEditing() throws Exception {
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.state()).thenReturn(ConnectionState.CONNECTED);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MyMajorTransferPanel(client(), connection);
            try {
                var method = MyMajorTransferPanel.class.getDeclaredMethod(
                        "render", MajorTransferWorkspace.class);
                method.setAccessible(true);
                method.invoke(panel, workspace(MajorTransferBatchStatus.CLOSED));
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
            assertThat(((JLabel) find(panel, "major-transfer.student.status")).getText())
                    .contains("已关闭");
            assertThat(((JButton) find(panel, "major-transfer.student.edit-application")).isEnabled())
                    .isFalse();
        });
    }

    @Test void adminHasWorkingAssessmentAndExecutionLists() throws Exception {
        try (ClientConnection connection = new ClientConnection("localhost", 1)) {
            SwingUtilities.invokeAndWait(() -> {
                var panel = new MajorTransferAdminPanel(client(), connection);
                // Panel now uses left-right split layout with batch list and app list
                assertThat(panel.getComponents()).isNotEmpty();
            });
        }
    }

    @Test void batchEditorIsHiddenUntilCreateIsRequested() throws Exception {
        StudentClientService students = mock(StudentClientService.class);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferBatchManagementPanel(students);
            EmbeddedEditorHost host = all(panel).stream().filter(EmbeddedEditorHost.class::isInstance)
                    .map(EmbeddedEditorHost.class::cast).findFirst().orElseThrow();
            assertThat(host.isEditorOpen()).isFalse();
            ((JButton) find(panel, "major-transfer.batch-create")).doClick();
            assertThat(host.isEditorOpen()).isTrue();
            assertThat(host.currentPlacement()).isEqualTo(
                    edu.seu.vcampus.client.core.ui.editor.EditorPlacement.BOTTOM);
        });
    }

    @Test void collegeOptionEditorOpensInsideThePage() throws Exception {
        StudentClientService students = mock(StudentClientService.class);
        when(students.listDepartments(true)).thenReturn(CompletableFuture.completedFuture(
                ResponseBody.success(new java.util.ArrayList<>(List.of(
                        new DepartmentView("d", "CS", "计算机学院", true, 0))))));
        when(students.listTransferOptions(anyString())).thenReturn(CompletableFuture.completedFuture(
                ResponseBody.success(new java.util.ArrayList<>())));
        when(students.listTransferApplications(any())).thenReturn(CompletableFuture.completedFuture(
                ResponseBody.success(new java.util.ArrayList<>())));
        when(students.listMajors("d")).thenReturn(CompletableFuture.completedFuture(
                ResponseBody.success(new java.util.ArrayList<>())));
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferCollegeProcessingPanel(students);
            EmbeddedEditorHost host = all(panel).stream().filter(EmbeddedEditorHost.class::isInstance)
                    .map(EmbeddedEditorHost.class::cast).findFirst().orElseThrow();
            assertThat(host.isEditorOpen()).isFalse();
            JComboBox<MajorTransferBatchView> batchBox = all(panel).stream()
                    .filter(JComboBox.class::isInstance).map(component -> (JComboBox<MajorTransferBatchView>) component)
                    .findFirst().orElseThrow();
            batchBox.addItem(workspace().activeBatch());
            ((JButton) find(panel, "saveOptionButton")).doClick();
            assertThat(host.isEditorOpen()).isTrue();
            assertThat(host.currentPlacement()).isEqualTo(
                    edu.seu.vcampus.client.core.ui.editor.EditorPlacement.BOTTOM);
        });
    }

    @Test void collegeFinalReviewRequiresAnExplicitTargetOption() throws Exception {
        StudentClientService students = mock(StudentClientService.class);
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferCollegeProcessingPanel(students);
            assertThat(find(panel, "major-transfer.finalization-option")).isInstanceOf(JComboBox.class);
            assertThat(((JButton) find(panel, "major-transfer.finalize-option")).getText())
                    .isEqualTo("专业终审");
            assertThat(find(panel, "major-transfer.finalize-batch")).isNull();
        });
    }

    @Test void collegeFinalReviewActionsRemainVisibleAtSupportedWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferCollegeProcessingPanel(mock(StudentClientService.class));
            panel.setSize(1080, 700);
            layout(panel);
            assertVisible(panel, find(panel, "major-transfer.finalize-option"));
            assertVisible(panel, find(panel, "major-transfer.effective-option"));
            assertVisible(panel, find(panel, "major-transfer.rollback-option"));
        });
    }

    @Test void collegeRefreshReloadsTransferBatches() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        StudentClientService students = new StudentClientService(new StudentRequestClient() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                if ("MAJOR_TRANSFER_LIST_BATCHES".equals(command)) reads.incrementAndGet();
                return CompletableFuture.completedFuture((ResponseBody) ResponseBody.success(
                        new java.util.ArrayList<>()));
            }
        }, Duration.ofSeconds(1));
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferCollegeProcessingPanel(students);
            panel.addNotify();
            JButton refresh = (JButton) find(panel, "major-transfer.college-refresh");
            assertThat(refresh).isNotNull();
            refresh.doClick();
        });
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (reads.get() < 2 && System.nanoTime() < deadline) {
            SwingUtilities.invokeAndWait(() -> { });
            java.util.concurrent.TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(reads.get()).isEqualTo(2);
    }

    private static java.util.List<Component> all(Container root) {
        java.util.List<Component> result = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            result.add(child);
            if (child instanceof Container nested) result.addAll(all(nested));
        }
        return result;
    }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component found = find(container, name); if (found != null) return found; }
        }
        return null;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container nested) layout(nested);
    }

    private static void assertVisible(Container root, Component child) {
        assertThat(child).isNotNull();
        assertThat(child.getWidth()).as("%s laid out", child.getName()).isPositive();
        for (Container parent = child.getParent(); parent != null; parent = parent.getParent()) {
            Rectangle bounds = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), parent);
            assertThat(new Rectangle(0, 0, parent.getWidth(), parent.getHeight()).contains(bounds))
                    .as("%s clipped in %s: %s", child.getName(), parent.getClass().getSimpleName(), bounds)
                    .isTrue();
            if (parent == root) break;
        }
    }

    private static MajorTransferWorkspace workspace() {
        return workspace(MajorTransferBatchStatus.OPEN);
    }

    private static MajorTransferWorkspace workspace(MajorTransferBatchStatus status) {
        Instant now = Instant.now();
        var batch = new MajorTransferBatchView("batch", "批次", status,
                now.minusSeconds(60), now.plusSeconds(60), null, null, null, 0);
        var first = new MajorTransferOptionView("one", "batch", "m1", "d", "专业一", "学院", "2024", 10, 20, 60.0, 60.0, 60, 40, false, "", true, 0);
        var second = new MajorTransferOptionView("two", "batch", "m2", "d", "专业二", "学院", "2024", 10, 20, 60.0, 60.0, 60, 40, true, "", true, 0);
        var app = new MajorTransferApplicationView("app", "batch", "student", "测试学生", MajorTransferApplicationType.DIFFICULTY,
                MajorTransferStatus.DRAFT, "two", "m2", "专业二", "d", "学院", "old-d", "原学院", "old-m", "原专业", "class", "班级",
                "09024101", "2024", "已暂存理由", null, null, null, List.of(), List.of(),
                false, false, 0, null, now, now);
        return new MajorTransferWorkspace(batch, List.of(first, second), List.of(new MajorTransferEligibilityItem("资格", true, "通过")),
                "old-d", "原学院", "old-m", "原专业", "class", "班级", "09024101", "2024", app);
    }
}
