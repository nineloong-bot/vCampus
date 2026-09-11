package edu.seu.vcampus.client.student.majortransfer;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferAdminPanel;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferAdminPanel.TransferAdminMode;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationType;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferApplicationView;
import edu.seu.vcampus.common.student.majortransfer.MajorTransferStatus;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MajorTransferRoleUiTest {
    @Test
    void centralModeShowsManagementButNeverCollegeReviewControls() throws Exception {
        try (ClientConnection connection = new ClientConnection("localhost", 1)) {
            SwingUtilities.invokeAndWait(() -> {
                var panel = new MajorTransferAdminPanel(client(), connection,
                        TransferAdminMode.CENTRAL_MANAGEMENT);
                assertThat(find(panel, "saveBatchButton")).isNotNull();
                render(panel, application(MajorTransferStatus.SUBMITTED));
                assertThat(find(panel, "sourceReviewButton")).isNull();
                render(panel, application(MajorTransferStatus.QUALIFIED));
                assertThat(find(panel, "recordScoreButton")).isNotNull();
            });
        }
    }

    @Test
    void collegeModeShowsOnlyStageApprovals() throws Exception {
        try (ClientConnection connection = new ClientConnection("localhost", 1)) {
            SwingUtilities.invokeAndWait(() -> {
                var panel = new MajorTransferAdminPanel(client(), connection,
                        TransferAdminMode.COLLEGE_APPROVAL);
                assertThat(find(panel, "saveBatchButton")).isNull();
                render(panel, application(MajorTransferStatus.SUBMITTED));
                assertThat(find(panel, "sourceReviewButton")).isNotNull();
                render(panel, application(MajorTransferStatus.SOURCE_APPROVED));
                assertThat(find(panel, "targetReviewButton")).isNotNull();
                render(panel, application(MajorTransferStatus.QUALIFIED));
                assertThat(find(panel, "recordScoreButton")).isNull();
            });
        }
    }

    private static StudentClientService client() {
        return new StudentClientService(new edu.seu.vcampus.client.student.service.StudentRequestClient() {
            @Override
            public <T extends Serializable> CompletableFuture<ResponseBody<T>> send(
                    String command, Serializable body, Duration timeout) {
                return CompletableFuture.completedFuture(
                        ResponseBody.failure("OFFLINE", "测试未连接", null));
            }
        }, Duration.ofSeconds(1));
    }

    private static MajorTransferApplicationView application(MajorTransferStatus status) {
        Instant now = Instant.now();
        return new MajorTransferApplicationView("app", "batch", "student", "测试学生",
                MajorTransferApplicationType.ORDINARY, status, "option", "target-major",
                "目标专业", "target-dept", "目标学院", "source-dept", "原学院",
                "source-major", "原专业", "class", "原班级", "09024101", "2024",
                "申请理由", null, null, null, List.of(), List.of(),
                status == MajorTransferStatus.SUBMITTED,
                status == MajorTransferStatus.SOURCE_APPROVED,
                0, now, now, now);
    }

    private static void render(MajorTransferAdminPanel panel,
                               MajorTransferApplicationView application) {
        try {
            var method = MajorTransferAdminPanel.class.getDeclaredMethod(
                    "renderDetail", MajorTransferApplicationView.class);
            method.setAccessible(true);
            method.invoke(panel, application);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container container) {
                Component found = find(container, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
