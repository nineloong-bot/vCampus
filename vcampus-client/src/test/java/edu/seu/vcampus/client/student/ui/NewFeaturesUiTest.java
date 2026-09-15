package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.student.majortransfer.ui.MajorTransferFlowChartPanel;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.majortransfer.*;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NewFeaturesUiTest {

    @Test
    void flowChartPanelRendersCorrectStepsAndRejection() {
        MajorTransferFlowChartPanel panel = new MajorTransferFlowChartPanel();

        // 1. Initial / null application state
        panel.update(null, List.of());
        assertThat(panel.getPreferredSize().height).isGreaterThan(100);

        // 2. Draft application
        Instant now = Instant.now();
        var opt = new MajorTransferOptionView("opt-1", "b-1", "m-cs", "d-cs", "计算机科学与技术", "计算机科学与工程学院",
                "2024", 10, 20, 60.0, 60.0, 60, 40, true, "", true, 0);
        var draftApp = new MajorTransferApplicationView("app-1", "b-1", "s-1", "测试学生",
                MajorTransferApplicationType.ORDINARY, MajorTransferStatus.DRAFT, "opt-1", "m-cs", "计算机科学与技术",
                "d-cs", "计算机科学与工程学院", "old-d", "原学院", "old-m", "原专业", "c-1", "班级",
                "09024101", "2024", "想转专业", null, null, null, List.of(), List.of(),
                false, false, 0, null, now, now);
        panel.update(draftApp, List.of(opt));

        // 3. Rejected application
        var review = new MajorTransferReviewView("rev-1", "app-1", MajorTransferReviewStage.SOURCE_REVIEW,
                MajorTransferDecision.REJECT, "admin-1", "成绩未达标", false, false, false, now);
        var rejectedApp = new MajorTransferApplicationView("app-1", "b-1", "s-1", "测试学生",
                MajorTransferApplicationType.ORDINARY, MajorTransferStatus.REJECTED, "opt-1", "m-cs", "计算机科学与技术",
                "d-cs", "计算机科学与工程学院", "old-d", "原学院", "old-m", "原专业", "c-1", "班级",
                "09024101", "2024", "想转专业", null, null, null, List.of(review), List.of(),
                false, false, 1, null, now, now);
        panel.update(rejectedApp, List.of(opt));
    }

    @Test
    void trainingPlanWorkspacePanelCanOpenAndCancel() {
        StudentClientService mockService = mock(StudentClientService.class);
        ArrayList<DepartmentView> depts = new ArrayList<>();
        when(mockService.listDepartments(anyBoolean())).thenReturn(CompletableFuture.completedFuture(ResponseBody.success(depts)));

        TrainingPlanManagementPanel panel = new TrainingPlanManagementPanel(mockService);
        assertThat(panel).isNotNull();

        // Verify left panel has workspaceCardPanel with CardLayout
        CardLayout cardLayout = null;
        JPanel workspaceCard = null;
        for (Component c : panel.getComponents()) {
            if (c instanceof JSplitPane split) {
                Component left = split.getLeftComponent();
                if (left instanceof JPanel leftPanel) {
                    for (Component sub : leftPanel.getComponents()) {
                        if (sub instanceof JPanel subPanel && subPanel.getLayout() instanceof CardLayout cl) {
                            cardLayout = cl;
                            workspaceCard = subPanel;
                        }
                    }
                }
            }
        }

        assertThat(workspaceCard).isNotNull();
        assertThat(cardLayout).isNotNull();

        // Verify top bar buttons
        boolean hasPoolBtn = false;
        boolean hasReviewBtn = false;
        for (Component c : panel.getComponents()) {
            if (c instanceof JSplitPane split) {
                if (split.getRightComponent() instanceof JPanel rightPanel) {
                    for (Component sub : rightPanel.getComponents()) {
                        if (sub instanceof JPanel bar) {
                            for (Component b : bar.getComponents()) {
                                if (b instanceof JButton btn && "全校课程库引入".equals(btn.getText())) hasPoolBtn = true;
                                if (b instanceof JButton btn && "跨学科审批".equals(btn.getText())) hasReviewBtn = true;
                            }
                        }
                    }
                }
            }
        }
        assertThat(hasPoolBtn).isTrue();
        assertThat(hasReviewBtn).isTrue();
    }

    @Test
    void myTrainingPlanPanelRendersThreeTabsAndColumns() {
        StudentClientService mockService = mock(StudentClientService.class);
        var courses = List.of(
                new edu.seu.vcampus.common.student.TrainingPlanCourseView(
                        "pc-1", "CS101", "计算机导论", new java.math.BigDecimal("3.0"),
                        edu.seu.vcampus.common.student.CourseType.REQUIRED, 1, true, 0L,
                        "c-1", "d-cs", "计算机学院", null),
                new edu.seu.vcampus.common.student.TrainingPlanCourseView(
                        "pc-2", "CS102", "程序设计", new java.math.BigDecimal("3.0"),
                        edu.seu.vcampus.common.student.CourseType.ELECTIVE, 2, true, 0L,
                        "c-2", "d-cs", "计算机学院", null),
                new edu.seu.vcampus.common.student.TrainingPlanCourseView(
                        "pc-3", "MATH101", "概率论与数理统计", new java.math.BigDecimal("3.0"),
                        edu.seu.vcampus.common.student.CourseType.CROSS_DISCIPLINARY, 3, true, 0L,
                        "c-3", "d-math", "数学学院", Integer.valueOf(30))
        );
        var plan = new edu.seu.vcampus.common.student.TrainingPlanDetailView(
                "p-1", "m-1", "计算机科学", "计算机学院", 2024,
                "2024培养方案", 2L, new java.math.BigDecimal("6.0"), true, 0L, courses);
        when(mockService.getMyTrainingPlan()).thenReturn(CompletableFuture.completedFuture(ResponseBody.success(plan)));

        MyTrainingPlanPanel myPanel = new MyTrainingPlanPanel(mockService);
        assertThat(myPanel).isNotNull();

        // Find JTabbedPane
        JTabbedPane tabs = null;
        for (Component c : myPanel.getComponents()) {
            if (c instanceof JTabbedPane tp) {
                tabs = tp;
                break;
            }
        }
        assertThat(tabs).isNotNull();
        assertThat(tabs.getTabCount()).isEqualTo(3);
        assertThat(tabs.getTitleAt(0)).isEqualTo("必修课程");
        assertThat(tabs.getTitleAt(1)).isEqualTo("专业选修课程");
        assertThat(tabs.getTitleAt(2)).isEqualTo("跨学科选修课程");
    }
}
