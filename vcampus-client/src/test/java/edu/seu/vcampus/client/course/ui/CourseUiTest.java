package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.paging.PageResult;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CourseUiTest {
    @Test
    void queryPageUsesReviewedTemplateAndSharedTokens() throws Exception {
        OfferingSearchPanel panel = onEdt(() -> new OfferingSearchPanel(CourseUiGateway.preview()));

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
    void scheduleUsesAWeeklyGridInsteadOfAPlainEnrollmentTable() throws Exception {
        MySchedulePanel panel = onEdt(() -> new MySchedulePanel(CourseUiGateway.preview()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(labels(panel)).contains("我的课表", "星期一", "星期二", "星期三", "星期四", "星期五");
        assertThat(descendants(panel).stream().filter(JPanel.class::isInstance)
                .map(JPanel.class::cast).anyMatch(p -> p.getLayout() instanceof java.awt.GridBagLayout)).isTrue();
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

    private static CourseUiGateway gateway(CompletableFuture<PageResult<OfferingSummary>> offerings) {
        return new CourseUiGateway() {
            public CompletableFuture<PageResult<OfferingSummary>> searchOfferings(OfferingSearchQuery query) { return offerings; }
            public CompletableFuture<List<EnrollmentView>> currentEnrollments() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<List<ScheduleItem>> currentSchedule() { return CompletableFuture.completedFuture(List.of()); }
            public CompletableFuture<EnrollmentView> enroll(EnrollCommand command) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
        };
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

    private static <T> T onEdt(java.util.concurrent.Callable<T> supplier) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> value = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { value.set(supplier.call()); } catch (Exception error) { throw new RuntimeException(error); }
        });
        return value.get();
    }
}
