package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;
import edu.seu.vcampus.common.course.RetakeCommand;
import edu.seu.vcampus.common.course.RetakeEligibility;
import edu.seu.vcampus.common.protocol.EmptyResponse;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.AdjustmentAuditQuery;
import edu.seu.vcampus.common.course.AdjustmentAuditView;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.TermView;
import edu.seu.vcampus.common.course.ImportCourseOutcomesCommand;
import edu.seu.vcampus.common.course.CourseOutcome;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.UpdateCourseCommand;
import edu.seu.vcampus.common.course.CreateTermCommand;
import edu.seu.vcampus.common.course.UpdateTermCommand;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.UpdateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingView;
import edu.seu.vcampus.common.course.TermPhaseView;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CourseUiTest {
    @Test
    void queryPageUsesReviewedTemplateAndSharedTokens() throws Exception {
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.getLayout()).isInstanceOf(BorderLayout.class);
        assertThat(panel.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(panel.getBorder().getBorderInsets(panel).top).isEqualTo(UiSpacing.PAGE_PADDING);
        assertThat(labels(panel)).contains("课程中心  /  教学班查询", "教学班查询", "共 3 条");
        assertThat(buttons(panel)).contains("查询教学班", "重置条件", "上一页", "下一页");
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        assertThat(table.getRowHeight()).isEqualTo(UiDimensions.TABLE_ROW_HEIGHT);
        assertThat(table.getShowVerticalLines()).isFalse();
        assertThat(table.getTableHeader().getBackground()).isEqualTo(UiColors.BACKGROUND_SUBTLE);
        assertThat(table.getValueAt(0, 4).toString()).startsWith("星期一");
        assertThat(descendants(panel)).anyMatch(JScrollPane.class::isInstance);
    }

    @Test
    void offeringSearchUsesResolvedTermIdAndSelectedWeekday() throws Exception {
        AtomicReference<OfferingSearchQuery> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<String> currentTermId() {
                return CompletableFuture.completedFuture("term-real-uuid");
            }
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                submitted.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> {
            descendants(panel).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .filter(combo -> "上课日期".equals(combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().setSelectedIndex(2);
            descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "查询教学班".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get().termId()).isEqualTo("term-real-uuid");
        assertThat(submitted.get().dayOfWeek()).isEqualTo("TUESDAY");
    }

    @Test
    void scheduleUsesAWeeklyGridInsteadOfAPlainEnrollmentTable() throws Exception {
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("我的课表", "星期一", "星期二", "星期三", "星期四", "星期五");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
        assertThat(descendants(panel).stream().filter(JPanel.class::isInstance)
                .map(JPanel.class::cast).anyMatch(p -> p.getLayout() instanceof java.awt.GridBagLayout)).isTrue();
    }

    @Test
    void scheduleShowsLoadingThenEmptyInsteadOfAnUnexplainedBlankGrid() throws Exception {
        CompletableFuture<List<ScheduleItem>> request = new CompletableFuture<>();
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(scheduleGateway(request)));

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.LOADING);
        request.complete(List.of());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.EMPTY);
        assertThat(labels(panel)).anyMatch(text -> text.contains("本学期还没有课程安排"));
    }

    @Test
    void scheduleShowsDisconnectedStateWhenLoadingFails() throws Exception {
        CompletableFuture<List<ScheduleItem>> failed = CompletableFuture.failedFuture(
                new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true));
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(scheduleGateway(failed)));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
        assertThat(labels(panel)).anyMatch(text -> text.contains("无法加载课表") && text.contains("检查连接"));
        assertThat(labels(panel)).noneMatch(text -> text.contains("socket details"));
    }

    @Test
    void publishesAllElevenCourseSurfaces() throws Exception {
        List<String> names = List.of("OfferingSearchPanel", "OfferingDetailDialog", "MyEnrollmentPanel",
                "MySchedulePanel", "AdjustmentPanel", "RetakePanel", "TermManagementPanel",
                "CourseCatalogPanel", "OfferingManagementPanel", "OutcomeImportPanel", "AdjustmentAuditPanel");
        for (String name : names) {
            assertThat(Class.forName(getClass().getPackageName() + "." + name)).isNotNull();
        }
    }

    @Test
    void compositionRegistersStudentAndAdministrativePagesUnderStableIds() throws Exception {
        CourseUiComposition composition = onEdt(() -> new CourseUiComposition(CourseUiGateway.preview()));

        assertThat(composition.studentPages().keySet()).containsExactly(
                "course.offerings", "course.enrollments", "course.schedule", "course.adjustment", "course.retake");
        assertThat(composition.administrativePages().keySet()).containsExactly(
                "course.terms", "course.catalog", "course.offering-admin", "course.outcome-import", "course.adjustment-audit");
        assertThat(composition.allPages()).hasSize(10);
    }

    @Test
    void queryShowsLoadingEmptyAndDisconnectedStatesWithActionableChineseText() throws Exception {
        CompletableFuture<PageResult<OfferingSummary>> request = new CompletableFuture<>();
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway(request)));
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.LOADING);

        request.complete(new PageResult<>(List.of(), 0, 20, 0));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.EMPTY);
        assertThat(labels(panel)).anyMatch(text -> text.contains("未找到教学班"));

        CompletableFuture<PageResult<OfferingSummary>> failed = CompletableFuture.failedFuture(
                new CourseClientException("COMMON_NETWORK_ERROR", "socket details", null, true));
        OfferingSearchPanel disconnected = onEdt(() -> new OfferingSearchPanel(gateway(failed)));
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(disconnected.viewState()).isEqualTo(AbstractCoursePanel.ViewState.DISCONNECTED);
        assertThat(labels(disconnected)).anyMatch(text -> text.contains("连接已断开"));
        assertThat(labels(disconnected)).noneMatch(text -> text.contains("socket details"));
    }

    @Test
    void selectedOfferingCanBeEnrolledAndSuccessRemainsVisible() throws Exception {
        AtomicReference<EnrollCommand> submitted = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return CourseUiGateway.preview().searchOfferings(query);
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            @Override public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "e1", command.offeringId(), "s1", "NORMAL", "ACTIVE", Instant.now(), null, 0));
            }
        };
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();
        JButton enroll = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "选择教学班".equals(button.getText())).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> { table.setRowSelectionInterval(0, 0); enroll.doClick(); });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get().offeringId()).isEqualTo("o1");
        assertThat(labels(panel)).anyMatch(text -> text.contains("课程已选上"));
    }

    @Test
    void myEnrollmentsLoadsRealGatewayRowsAsynchronously() throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return base.searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(new EnrollmentView(
                        "e1", "offering-real-1", "student-real", "NORMAL", "ACTIVE", Instant.parse("2026-08-28T08:00:00Z"), null, 2)));
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("offering-real-1");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void removedEnrollmentPageIgnoresLateAsyncResults() throws Exception {
        CompletableFuture<List<EnrollmentView>> request = new CompletableFuture<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return request; }
        };
        MyEnrollmentPanel panel = onEdt(() -> new MyEnrollmentPanel(gateway));
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(panel::removeNotify);

        request.complete(List.of(new EnrollmentView(
                "late", "offering-late", "student-late", "NORMAL", "ACTIVE",
                Instant.parse("2026-08-28T08:00:00Z"), null, 0)));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getRowCount()).isZero();
    }

    @Test
    void adjustmentChangeUsesSelectedEnrollmentTargetAndExpectedVersion() throws Exception {
        AtomicReference<ChangeOfferingCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return base.searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of(new EnrollmentView(
                        "enrollment-source", "o1", "s1", "NORMAL", "ACTIVE", Instant.now(), null, 7)));
            }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
            @Override public CompletableFuture<EnrollmentView> lateAdd(LateAddCommand command) { return base.enroll(new EnrollCommand(command.offeringId())); }
            @Override public CompletableFuture<EmptyResponse> drop(DropCommand command) { return CompletableFuture.completedFuture(EmptyResponse.INSTANCE); }
            @Override public CompletableFuture<EnrollmentView> change(ChangeOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "enrollment-source", command.targetOfferingId(), "s1", "NORMAL", "ACTIVE", Instant.now(), null, 8));
            }
            @Override public CompletableFuture<RetakeEligibility> checkRetake(String courseId) { return CompletableFuture.completedFuture(new RetakeEligibility(courseId, false, List.of(), "")); }
            @Override public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) { return base.enroll(new EnrollCommand(command.offeringId())); }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return CompletableFuture.completedFuture(new TermPhaseView(termId, "ACTIVE", "ADJUSTMENT",
                        Instant.parse("2026-09-03T00:00:00Z"), Instant.parse("2026-08-20T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-08T16:00:00Z")));
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway,
                (owner, source, target, conflict, request, onSuccess) -> {
                    request.get().join();
                    onSuccess.run();
                }));
        SwingUtilities.invokeAndWait(() -> { });
        List<JTable> tables = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).toList();
        JButton change = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow();

        assertThat(tables.get(0).getColumnName(0)).isEqualTo("课程");
        assertThat(tables.get(0).getValueAt(0, 0)).isEqualTo("高等数学");
        assertThat(tables.get(0).getValueAt(0, 1)).isEqualTo("01班");
        assertThat(tables.get(0).getValueAt(0, 2)).isEqualTo("正常选课");
        assertThat(tables.get(0).getValueAt(0, 3)).isEqualTo("有效");
        assertThat(labels(panel)).anyMatch(text -> text.contains("退改补开放") && text.contains("09-01") && text.contains("09-09"));

        SwingUtilities.invokeAndWait(() -> {
            tables.get(0).setRowSelectionInterval(0, 0);
            tables.get(1).setRowSelectionInterval(1, 1);
            change.doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new ChangeOfferingCommand("enrollment-source", "o2", 7));
        assertThat(labels(panel)).anyMatch(text -> text.contains("改选成功"));
    }

    @Test
    void adjustmentShowsAnActionableEmptyStateWhenNothingCanBeAdjusted() throws Exception {
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<String> currentTermId() { return base.currentTermId(); }
            @Override public CompletableFuture<TermPhaseView> getTermPhase(String termId) {
                return base.getTermPhase(termId);
            }
            @Override public CompletableFuture<List<EnrollmentView>> currentEnrollments() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() {
                return CompletableFuture.completedFuture(List.of());
            }
            @Override public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                return CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 100, 0));
            }
        };
        AdjustmentPanel panel = onEdt(() -> new AdjustmentPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.EMPTY);
        assertThat(labels(panel)).anyMatch(text -> text.contains("没有可调整的选课或教学班"));
    }

    @Test
    void retakeRequiresEligibilityCheckBeforeSubmittingSelectedOffering() throws Exception {
        AtomicReference<String> checkedCourse = new AtomicReference<>();
        AtomicReference<RetakeCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return base.searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return base.enroll(command); }
            @Override public CompletableFuture<RetakeEligibility> checkRetake(String courseId) {
                checkedCourse.set(courseId);
                return CompletableFuture.completedFuture(new RetakeEligibility(courseId, true, List.of("failed-1"), "FAILED_ATTEMPT"));
            }
            @Override public CompletableFuture<EnrollmentView> enrollRetake(RetakeCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new EnrollmentView(
                        "retake-1", command.offeringId(), "s1", "RETAKE", "ACTIVE", Instant.now(), null, 0));
            }
        };
        RetakePanel panel = onEdt(() -> new RetakePanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();
        JButton check = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "检查重修资格".equals(button.getText())).findFirst().orElseThrow();
        JButton enroll = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认重修".equals(button.getText())).findFirst().orElseThrow();

        assertThat(enroll.isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> { table.setRowSelectionInterval(0, 0); check.doClick(); });
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(checkedCourse.get()).isEqualTo("c1");
        assertThat(enroll.isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(enroll::doClick);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new RetakeCommand("o1"));
        assertThat(labels(panel)).anyMatch(text -> text.contains("重修选课成功"));
    }

    @Test
    void adjustmentAuditLoadsLiveRowsAndUsesAdministrativeQuery() throws Exception {
        AtomicReference<AdjustmentAuditQuery> captured = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<PageResult<AdjustmentAuditView>> searchAdjustmentAudits(AdjustmentAuditQuery query) {
                captured.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(new AdjustmentAuditView(
                        "a1", "20260001", "CHANGE", "math-01", "math-02", "SUCCEEDED", null,
                        Instant.parse("2026-08-28T08:30:00Z"))), 0, 50, 1));
            }
        };

        AdjustmentAuditPanel panel = onEdt(() -> new AdjustmentAuditPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(captured.get()).isEqualTo(new AdjustmentAuditQuery(null, null, null, null, 0, 50));
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 1)).isEqualTo("20260001");
        assertThat(table.getValueAt(0, 2)).isEqualTo("改选");
        assertThat(table.getValueAt(0, 5)).isEqualTo("成功");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void courseCatalogLoadsLiveRowsWithCatalogQuery() throws Exception {
        AtomicReference<CourseCatalogQuery> captured = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                captured.set(query);
                return CompletableFuture.completedFuture(new PageResult<>(List.of(new CourseView(
                        "c1", "MATH101", "高等数学", new BigDecimal("5.0"), 80, "理工科基础课程", true, 3,
                        Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"))), 0, 50, 1));
            }
        };

        CourseCatalogPanel panel = onEdt(() -> new CourseCatalogPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(captured.get()).isEqualTo(new CourseCatalogQuery("", null, 0, 50));
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("MATH101");
        assertThat(table.getValueAt(0, 4)).isEqualTo("启用");
        assertThat(buttons(panel)).contains("新建课程", "编辑所选");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void courseCatalogKeepsTheLatestQueryWhenAnOlderResponseArrivesLate() throws Exception {
        CompletableFuture<PageResult<CourseView>> older = new CompletableFuture<>();
        CourseView newestCourse = courseView("new", "NEW101", "新查询课程");
        CourseView olderCourse = courseView("old", "OLD101", "旧查询课程");
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<PageResult<CourseView>> searchCatalog(CourseCatalogQuery query) {
                if (calls.getAndIncrement() == 0) return older;
                return CompletableFuture.completedFuture(new PageResult<>(List.of(newestCourse), 0, 50, 1));
            }
        };
        CourseCatalogPanel panel = onEdt(() -> new CourseCatalogPanel(gateway));
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance)
                .map(JTable.class::cast).findFirst().orElseThrow();
        SwingUtilities.invokeAndWait(() -> descendants(panel).stream().filter(JButton.class::isInstance)
                .map(JButton.class::cast).filter(button -> "查询课程".equals(button.getText()))
                .findFirst().orElseThrow().doClick());
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(table.getValueAt(0, 0)).isEqualTo("NEW101");

        older.complete(new PageResult<>(List.of(olderCourse), 0, 50, 1));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(table.getValueAt(0, 0)).isEqualTo("NEW101");
    }

    @Test
    void termManagementLoadsLiveWindowsAndChineseStatus() throws Exception {
        AtomicReference<Boolean> requested = new AtomicReference<>(false);
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<List<TermView>> listTerms() {
                requested.set(true);
                return CompletableFuture.completedFuture(List.of(new TermView(
                        "t1", "2026-2027-1", "2026—2027学年秋季学期", LocalDate.parse("2026-09-01"), LocalDate.parse("2027-01-15"),
                        Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-31T16:00:00Z"),
                        Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-08T16:00:00Z"), "ACTIVE", 4,
                        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"))));
            }
        };

        TermManagementPanel panel = onEdt(() -> new TermManagementPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(requested.get()).isTrue();
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo("2026-2027-1");
        assertThat(table.getValueAt(0, 4)).isEqualTo("进行中");
        assertThat(table.getValueAt(0, 5)).isEqualTo("v4");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void offeringManagementLoadsLiveOfferingRows() throws Exception {
        AtomicReference<OfferingSearchQuery> captured = new AtomicReference<>();
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
                captured.set(query);
                return base.searchOfferings(query);
            }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };

        OfferingManagementPanel panel = onEdt(() -> new OfferingManagementPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });
        JTable table = descendants(panel).stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();

        assertThat(captured.get()).isEqualTo(new OfferingSearchQuery("2026-autumn", "", null, false, 0, 50));
        assertThat(table.getRowCount()).isEqualTo(3);
        assertThat(table.getValueAt(0, 0)).isEqualTo("MATH101");
        assertThat(table.getValueAt(0, 6)).isEqualTo("开放");
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void outcomeImportParsesPassFailRowsAndSubmitsTypedCommand() throws Exception {
        AtomicReference<ImportCourseOutcomesCommand> submitted = new AtomicReference<>();
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<EmptyResponse> importOutcomes(ImportCourseOutcomesCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(EmptyResponse.INSTANCE);
            }
        };
        OutcomeImportPanel panel = onEdt(() -> new OutcomeImportPanel(gateway));
        JTextArea input = descendants(panel).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).findFirst().orElseThrow();
        JButton submit = descendants(panel).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "导入课程结果".equals(button.getText())).findFirst().orElseThrow();

        SwingUtilities.invokeAndWait(() -> {
            input.setText("student-1,course-1,term-1,FAILED,registrar-2026-001");
            submit.doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry("student-1", "course-1", "term-1", CourseOutcome.FAILED, "registrar-2026-001"))));
        assertThat(labels(panel)).anyMatch(text -> text.contains("已导入 1 条"));
        assertThat(panel.viewState()).isEqualTo(AbstractCoursePanel.ViewState.NORMAL);
    }

    @Test
    void administrativeFilterFieldsHaveVisibleChineseLabels() throws Exception {
        CourseUiGateway gateway = CourseUiGateway.preview();
        CourseCatalogPanel catalog = onEdt(() -> new CourseCatalogPanel(gateway));
        OfferingManagementPanel offerings = onEdt(() -> new OfferingManagementPanel(gateway));
        AdjustmentAuditPanel audits = onEdt(() -> new AdjustmentAuditPanel(gateway));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(catalog)).contains("课程关键字");
        assertThat(labels(offerings)).contains("学期编号", "课程或教学班");
        assertThat(labels(audits)).contains("学生编号", "学期编号", "操作类型", "操作结果");
    }

    @Test
    void courseEditorSubmitsTypedCreateCommandFromVisibleFields() throws Exception {
        AtomicReference<CreateCourseCommand> submitted = new AtomicReference<>();
        AtomicReference<Boolean> saved = new AtomicReference<>(false);
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new CourseView(
                        "c-new", command.courseCode(), command.courseName(), command.credit(), command.totalHours(),
                        command.description(), command.active(), 0, Instant.now(), Instant.now()));
            }
        };
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(null, gateway, null, () -> saved.set(true)));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "课程代码").setText("SE101");
            textField(dialog, "课程名称").setText("软件工程导论");
            textField(dialog, "学分").setText("4.5");
            textField(dialog, "总学时").setText("72");
            descendants(dialog).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).findFirst().orElseThrow()
                    .setText("软件工程基础课程");
            descendants(dialog).stream().filter(JCheckBox.class::isInstance).map(JCheckBox.class::cast).findFirst().orElseThrow()
                    .setSelected(true);
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "创建课程".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new CreateCourseCommand(
                "SE101", "软件工程导论", new BigDecimal("4.5"), 72, "软件工程基础课程", true));
        assertThat(saved.get()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void disposedCourseEditorIgnoresALateSaveResult() throws Exception {
        CompletableFuture<CourseView> pending = new CompletableFuture<>();
        AtomicReference<Boolean> saved = new AtomicReference<>(false);
        CourseUiGateway base = CourseUiGateway.preview();
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<CourseView> createCourse(CreateCourseCommand command) {
                return pending;
            }
        };
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(null, gateway, null, () -> saved.set(true)));
        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "课程代码").setText("LATE101");
            textField(dialog, "课程名称").setText("迟到响应测试");
            textField(dialog, "学分").setText("2.0");
            textField(dialog, "总学时").setText("32");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "创建课程".equals(button.getText())).findFirst().orElseThrow().doClick();
            dialog.dispose();
        });

        pending.complete(courseView("late", "LATE101", "迟到响应测试"));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(saved.get()).isFalse();
    }

    @Test
    void courseEditorPreservesIdAndVersionWhenUpdating() throws Exception {
        AtomicReference<UpdateCourseCommand> submitted = new AtomicReference<>();
        CourseView existing = new CourseView("course-7", "SE101", "软件工程导论", new BigDecimal("4.5"), 72,
                "原简介", true, 7, Instant.now(), Instant.now());
        CourseUiGateway gateway = new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return CourseUiGateway.preview().searchOfferings(query); }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
            @Override public CompletableFuture<CourseView> updateCourse(UpdateCourseCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(existing);
            }
        };
        CourseEditorDialog dialog = onEdt(() -> new CourseEditorDialog(null, gateway, existing, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "课程名称").setText("软件工程基础");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "保存修改".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new UpdateCourseCommand(
                "course-7", "SE101", "软件工程基础", new BigDecimal("4.5"), 72, "原简介", true, 7));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void termEditorSubmitsCompleteTypedCreateCommand() throws Exception {
        AtomicReference<CreateTermCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = gateway(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)));
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<TermView> createTerm(CreateTermCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new TermView("term-new", command.termCode(), command.termName(),
                        command.startDate(), command.endDate(), command.enrollmentStartAt(), command.enrollmentEndAt(),
                        command.adjustmentStartAt(), command.adjustmentEndAt(), command.termStatus(), 0, Instant.now(), Instant.now()));
            }
        };
        TermEditorDialog dialog = onEdt(() -> new TermEditorDialog(null, gateway, null, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "学期代码").setText("2027-2028-1");
            textField(dialog, "学期名称").setText("2027—2028学年秋季学期");
            textField(dialog, "开学日期").setText("2027-09-01");
            textField(dialog, "结束日期").setText("2028-01-15");
            textField(dialog, "选课开始").setText("2027-08-20 08:00");
            textField(dialog, "选课结束").setText("2027-08-31 23:59");
            textField(dialog, "退改补开始").setText("2027-09-01 08:00");
            textField(dialog, "退改补结束").setText("2027-09-08 23:59");
            descendants(dialog).stream().filter(JComboBox.class::isInstance).map(JComboBox.class::cast)
                    .findFirst().orElseThrow().setSelectedItem("ACTIVE");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "创建学期".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new CreateTermCommand("2027-2028-1", "2027—2028学年秋季学期",
                LocalDate.parse("2027-09-01"), LocalDate.parse("2028-01-15"),
                Instant.parse("2027-08-20T00:00:00Z"), Instant.parse("2027-08-31T15:59:00Z"),
                Instant.parse("2027-09-01T00:00:00Z"), Instant.parse("2027-09-08T15:59:00Z"), "ACTIVE"));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void termEditorPreservesIdentityAndOptimisticVersionWhenUpdating() throws Exception {
        TermView existing = CourseUiGateway.preview().listTerms().join().get(0);
        AtomicReference<UpdateTermCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = gateway(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)));
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<TermView> updateTerm(UpdateTermCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(existing);
            }
        };
        TermEditorDialog dialog = onEdt(() -> new TermEditorDialog(null, gateway, existing, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "学期名称").setText("秋季学期（调整）");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "保存修改".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get().termId()).isEqualTo(existing.termId());
        assertThat(submitted.get().termName()).isEqualTo("秋季学期（调整）");
        assertThat(submitted.get().expectedVersion()).isEqualTo(existing.rowVersion());
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringEditorSubmitsAggregateScheduleAndPreservesVersion() throws Exception {
        OfferingSummary existing = CourseUiGateway.preview()
                .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, false, 0, 20)).join().items().get(0);
        AtomicReference<UpdateOfferingCommand> submitted = new AtomicReference<>();
        CourseUiGateway base = gateway(CompletableFuture.completedFuture(new PageResult<>(List.of(), 0, 20, 0)));
        CourseUiGateway gateway = new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<OfferingView> updateOffering(UpdateOfferingCommand command) {
                submitted.set(command);
                return CompletableFuture.completedFuture(new OfferingView(command.offeringId(), command.termId(),
                        command.courseId(), command.teacherUserId(), command.className(), command.capacity(),
                        existing.enrolledCount(), command.offeringStatus(), command.expectedVersion() + 1,
                        Instant.now(), Instant.now(), existing.schedules()));
            }
        };
        OfferingEditorDialog dialog = onEdt(() -> new OfferingEditorDialog(null, gateway, existing, () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            textField(dialog, "教师用户编号").setText("teacher-9");
            textField(dialog, "容量").setText("48");
            descendants(dialog).stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                    .findFirst().orElseThrow().setText("周二,3,4,1,16,教一-203\nTHURSDAY,5,6,2,15,教二-301");
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "保存修改".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(submitted.get()).isEqualTo(new UpdateOfferingCommand(existing.offeringId(), existing.termId(),
                existing.courseId(), "teacher-9", existing.className(), 48, existing.offeringStatus(),
                existing.rowVersion(), List.of(
                new CreateOfferingCommand.ScheduleInput("TUESDAY", 3, 4, 1, 16, "教一-203"),
                new CreateOfferingCommand.ScheduleInput("THURSDAY", 5, 6, 2, 15, "教二-301"))));
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringChangeDialogShowsRequiredComparisonAndConfirmsExplicitly() throws Exception {
        List<OfferingSummary> offerings = CourseUiGateway.preview()
                .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 20)).join().items();
        OfferingSummary source = offerings.get(0);
        ScheduleItem targetSchedule = new ScheduleItem("s1b", "o1b", source.courseCode(), source.courseName(),
                "02班", "赵老师", "TUESDAY", 3, 4, 1, 16, "教一-203");
        OfferingSummary target = new OfferingSummary("o1b", source.termId(), source.courseId(), source.courseCode(),
                source.courseName(), "赵老师", "02班", 40, 31, "OPEN", 0, List.of(targetSchedule));
        AtomicReference<Boolean> confirmed = new AtomicReference<>(false);
        OfferingDetailDialog dialog = onEdt(() -> new OfferingDetailDialog(
                null, source, target, "未发现时间冲突（服务端提交时将再次校验）",
                () -> confirmed.set(true)));

        assertThat(labels(dialog)).contains("原教学班", "目标教学班", "高等数学 · 01班", "高等数学 · 02班",
                "容量：28 / 40", "未发现时间冲突（服务端提交时将再次校验）");
        SwingUtilities.invokeAndWait(() -> descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow().doClick());

        assertThat(confirmed.get()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    @Test
    void offeringChangeDialogStaysOpenAndExplainsServerRejection() throws Exception {
        OfferingSummary source = CourseUiGateway.preview()
                .searchOfferings(new OfferingSearchQuery("2026-autumn", "", null, true, 0, 20)).join().items().get(0);
        ScheduleItem targetSchedule = new ScheduleItem("s1b", "o1b", source.courseCode(), source.courseName(),
                "02班", "赵老师", "TUESDAY", 3, 4, 1, 16, "教一-203");
        OfferingSummary target = new OfferingSummary("o1b", source.termId(), source.courseId(), source.courseCode(),
                source.courseName(), "赵老师", "02班", 40, 40, "OPEN", 0, List.of(targetSchedule));
        OfferingDetailDialog dialog = onEdt(() -> new OfferingDetailDialog(
                null, source, target, "未发现时间冲突（服务端提交时将再次校验）",
                () -> CompletableFuture.failedFuture(new CourseClientException(
                        "COURSE_OFFERING_FULL", "internal capacity detail", null, false)), () -> { }));

        SwingUtilities.invokeAndWait(() -> {
            dialog.setModal(false);
            dialog.setVisible(true);
            descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow().doClick();
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(dialog.isShowing()).isTrue();
        assertThat(labels(dialog)).contains("教学班容量已满，请选择其他教学班");
        assertThat(descendants(dialog).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> "确认改选".equals(button.getText())).findFirst().orElseThrow().isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(dialog::dispose);
    }

    private static CourseUiGateway gateway(CompletableFuture<PageResult<OfferingSummary>> offerings) {
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return offerings; }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };
    }

    private static CourseUiGateway scheduleGateway(CompletableFuture<List<ScheduleItem>> schedule) {
        CourseUiGateway base = CourseUiGateway.preview();
        return new DelegatingCourseUiGateway(base) {
            @Override public CompletableFuture<List<ScheduleItem>> currentSchedule() { return schedule; }
        };
    }

    private static CourseView courseView(String id, String code, String name) {
        return new CourseView(id, code, name, new BigDecimal("2.0"), 32, "测试课程", true, 0,
                Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-28T00:00:00Z"));
    }

    private static List<Component> descendants(Container root) {
        List<Component> all = new ArrayList<>();
        for (Component child : root.getComponents()) {
            all.add(child);
            if (child instanceof Container nested) all.addAll(descendants(nested));
        }
        return all;
    }

    private static List<String> labels(Container root) {
        return descendants(root).stream().filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .map(JLabel::getText).toList();
    }

    private static List<String> buttons(Container root) {
        return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .map(JButton::getText).toList();
    }

    private static JTextField textField(Container root, String accessibleName) {
        return descendants(root).stream().filter(JTextField.class::isInstance).map(JTextField.class::cast)
                .filter(field -> accessibleName.equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private abstract static class DelegatingCourseUiGateway implements CourseUiGateway {
        private final CourseUiGateway delegate;

        private DelegatingCourseUiGateway(CourseUiGateway delegate) { this.delegate = delegate; }
        public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) {
            return delegate.searchOfferings(query);
        }
        public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return delegate.currentEnrollments(); }
        public CompletableFuture<List<ScheduleItem>> currentSchedule() { return delegate.currentSchedule(); }
        public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return delegate.enroll(command); }
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> supplier) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(supplier.call()); } catch (Exception error) { throw new RuntimeException(error); }
        });
        return value.get();
    }
}
