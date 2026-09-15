package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.TermPhaseView;
import edu.seu.vcampus.common.paging.PageResult;

import javax.swing.SwingUtilities;
import java.util.List;

/** Asynchronous adjustment data loading for the adjustment panel segments. */
abstract class AdjustmentPanelRefreshing extends AdjustmentPanelActions {

    AdjustmentPanelRefreshing(CourseUiGateway gateway) {
        super(gateway);
    }

    AdjustmentPanelRefreshing(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super(gateway, confirmation);
    }

    @Override void refresh() {
        refresh("");
    }

    @Override void refresh(String successMessage) {
        long request = beginAsyncRequest();
        setActionButtonsEnabled(false);
        showState(ViewState.LOADING, "正在加载调整数据，请稍候");
        var enrollmentRequest = gateway.currentEnrollments();
        var termRequest = gateway.currentTermId();
        var offeringRequest = termRequest.thenCompose(term -> gateway.searchOfferings(
                new OfferingSearchQuery(term, "", null, true, 0, 100)));
        var phaseRequest = termRequest.thenCompose(gateway::getTermPhase);
        var scheduleRequest = gateway.currentSchedule();
        enrollmentRequest.thenCombine(offeringRequest, PartialData::new).thenCombine(phaseRequest,
                (partial, phase) -> new PartialDataWithPhase(partial.enrollments(), partial.offerings(), phase))
                .thenCombine(scheduleRequest, (partial, schedule) -> new Data(
                        partial.enrollments(), partial.offerings(), partial.phase(), schedule)).whenComplete((data, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (!acceptsAsyncResult(request)) return;
                    if (error != null) {
                        adjustmentOpen = false;
                        setActionButtonsEnabled(false);
                        showState(ViewState.DISCONNECTED, "无法加载调整数据，请检查连接后重试");
                        return;
                    }
                    enrollmentModel.setRowCount(0);
                    offeringModel.setRowCount(0);
                    enrollments.clear();
                    offerings.clear();
                    currentSchedule.clear();
                    enrollments.addAll(data.enrollments());
                    offerings.addAll(data.offerings().items());
                    currentSchedule.addAll(data.schedule());
                    phaseSummary.setText(phaseText(data.phase()));
                    adjustmentOpen = "ADJUSTMENT".equals(data.phase().phase())
                            && !"CLOSED".equals(data.phase().termStatus());
                    setActionButtonsEnabled(adjustmentOpen);
                    for (EnrollmentView row : enrollments) {
                        OfferingSummary offering = findOffering(row.offeringId());
                        enrollmentModel.addRow(new Object[]{
                                offering == null ? "课程信息待同步" : offering.courseName(),
                                offering == null ? "教学班信息待同步" : offering.className(),
                                enrollmentType(row.enrollmentType()), enrollmentStatus(row.enrollmentStatus()),
                                "v" + row.rowVersion()});
                    }
                    for (OfferingSummary row : offerings) offeringModel.addRow(new Object[]{
                            row.courseCode(), row.courseName(), row.className(),
                            Math.max(0, row.capacity() - row.enrolledCount()) + " / " + row.capacity(), "可调整"});
                    boolean empty = enrollments.isEmpty() && offerings.isEmpty();
                    showState(empty ? ViewState.EMPTY : ViewState.NORMAL,
                            successMessage.isBlank()
                                    ? (empty ? "当前没有可调整的选课或教学班，请稍后刷新" : "")
                                    : successMessage);
                }));
    }

    /** Partial load result before the phase is known. */
    record PartialData(List<EnrollmentView> enrollments, PageResult<OfferingSummary> offerings) { }
    /** Partial load result once the phase is known. */
    record PartialDataWithPhase(List<EnrollmentView> enrollments,
                                        PageResult<OfferingSummary> offerings,
                                        TermPhaseView phase) { }
    /** Complete load result driving the adjustment page. */
    record Data(List<EnrollmentView> enrollments, PageResult<OfferingSummary> offerings,
                        TermPhaseView phase, List<ScheduleItem> schedule) { }
}
