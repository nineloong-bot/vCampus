package edu.seu.vcampus.client.student.majortransfer;

import edu.seu.vcampus.client.core.network.*;
import edu.seu.vcampus.client.student.service.*;
import edu.seu.vcampus.client.student.majortransfer.ui.*;
import edu.seu.vcampus.common.student.majortransfer.*;
import edu.seu.vcampus.common.protocol.ResponseBody;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.time.*;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    @Test void adminHasWorkingAssessmentAndExecutionLists() throws Exception {
        try (ClientConnection connection = new ClientConnection("localhost", 1)) {
            SwingUtilities.invokeAndWait(() -> {
                var panel = new MajorTransferAdminPanel(client(), connection);
                // Panel now uses left-right split layout with batch list and app list
                assertThat(panel.getComponents()).isNotEmpty();
            });
        }
    }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) { Component found = find(container, name); if (found != null) return found; }
        }
        return null;
    }

    private static MajorTransferWorkspace workspace() {
        Instant now = Instant.now();
        var batch = new MajorTransferBatchView("batch", "批次", MajorTransferBatchStatus.OPEN,
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
