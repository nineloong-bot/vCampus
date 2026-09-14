package edu.seu.vcampus.client.student.majortransfer;

import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferBatchManagementPanel;
import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferCollegeProcessingPanel;
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
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferBatchManagementPanel(client());
            assertThat(find(panel, "saveBatchButton")).isNotNull();
            assertThat(find(panel, "sourceReviewButton")).isNull();
            assertThat(find(panel, "recordScoreButton")).isNull();
            assertThat(find(panel, "executeButton")).isNull();
        });
    }

    @Test
    void collegeModeShowsOptionAndTargetProcessingControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new MajorTransferCollegeProcessingPanel(client());
            assertThat(find(panel, "saveBatchButton")).isNull();
            assertThat(find(panel, "saveOptionButton")).isNotNull();
            ownOption(panel, "option");
            render(panel, application(MajorTransferStatus.SUBMITTED));
            assertThat(find(panel, "sourceReviewButton")).isNotNull();
            render(panel, application(MajorTransferStatus.SOURCE_APPROVED));
            assertThat(find(panel, "targetReviewButton")).isNotNull();
            render(panel, application(MajorTransferStatus.QUALIFIED));
            assertThat(find(panel, "recordScoreButton")).isNotNull();
            render(panel, application(MajorTransferStatus.PENDING_EFFECTIVE));
            assertThat(find(panel, "executeButton")).isNotNull();
            render(panel, applicationWithAttachment());
            assertThat(find(panel, "downloadAttachmentButton")).isNotNull();
        });
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

    private static MajorTransferApplicationView applicationWithAttachment() {
        MajorTransferApplicationView app = application(MajorTransferStatus.SUBMITTED);
        return new MajorTransferApplicationView(app.applicationId(), app.batchId(), app.studentId(),
                app.studentName(), app.applicationType(), app.status(), app.optionId(),
                app.targetMajorId(), app.targetMajorName(), app.targetDepartmentId(),
                app.targetDepartmentName(), app.fromDepartmentId(), app.fromDepartmentName(),
                app.fromMajorId(), app.fromMajorName(), app.fromClassId(), app.fromClassName(),
                app.fromStudentNumber(), app.fromGrade(), app.reason(), app.writtenScore(),
                app.interviewScore(), app.finalScore(), app.reviews(),
                List.of(new MajorTransferApplicationView.AttachmentInfo(
                        "attachment", "证明.pdf", "application/pdf", 1024)),
                app.sourceApprovalAllowed(), app.targetApprovalAllowed(),
                app.applicationVersion(), app.submittedAt(), app.createdAt(), app.updatedAt());
    }

    private static void render(MajorTransferCollegeProcessingPanel panel,
                               MajorTransferApplicationView application) {
        try {
            var method = MajorTransferCollegeProcessingPanel.class.getDeclaredMethod(
                    "renderDetail", MajorTransferApplicationView.class);
            method.setAccessible(true);
            method.invoke(panel, application);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void ownOption(MajorTransferCollegeProcessingPanel panel, String optionId) {
        try {
            var field = MajorTransferCollegeProcessingPanel.class.getDeclaredField("ownedOptions");
            field.setAccessible(true);
            ((java.util.Set<String>) field.get(panel)).add(optionId);
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
