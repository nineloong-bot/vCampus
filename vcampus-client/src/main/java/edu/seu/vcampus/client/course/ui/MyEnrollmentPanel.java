package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.OfferingSummary;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface DropConfirmation {
    boolean confirm(java.awt.Window owner, String courseLabel);
}

/** Read-only current-term enrollment list; all selection mutations live on the unified selection page. */
public final class MyEnrollmentPanel extends AbstractCoursePanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private final CourseUiGateway gateway;
    private final JLabel summary = label("共 0 条", UiTypography.BODY, UiColors.TEXT_SECONDARY);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"教学班编号", "课程名称", "教师", "选课类型", "状态", "选课时间"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<EnrollmentView> enrollments = new ArrayList<>();

    public MyEnrollmentPanel(CourseUiGateway gateway) {
        this(gateway, (owner, courseLabel) -> false, () -> { });
    }

    MyEnrollmentPanel(CourseUiGateway gateway, DropConfirmation ignoredConfirmation,
                      Runnable ignoredOnEnrollmentChanged) {
        super("我的选课");
        this.gateway = gateway;
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(UiColors.BACKGROUND_SUBTLE);
        toolbar.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG,
                UiSpacing.MD, UiSpacing.LG));
        toolbar.add(label("此页面仅供查询，选退操作统一在“选课”页完成。",
                UiTypography.BODY, UiColors.TEXT_PRIMARY), BorderLayout.CENTER);
        JButton refresh = primary("刷新选课");
        refresh.addActionListener(event -> refresh());
        toolbar.add(refresh, BorderLayout.EAST);
        body.add(toolbar, BorderLayout.NORTH);

        table.setModel(model);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("我的选课记录");
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(summary, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        listing.add(scroll, BorderLayout.CENTER);
        body.add(listing, BorderLayout.CENTER);
        refresh();
    }

    public void refresh() {
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在加载我的选课，请稍候");
        loadRows().whenComplete((values, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) {
                showState(ViewState.DISCONNECTED, "无法加载我的选课，请检查连接后重试");
                return;
            }
            model.setRowCount(0);
            enrollments.clear();
            enrollments.addAll(values.stream().map(EnrollmentRow::enrollment).toList());
            for (EnrollmentRow row : values) {
                EnrollmentView enrollment = row.enrollment();
                model.addRow(new Object[]{enrollment.offeringId(), row.courseName(), row.teacherName(),
                        typeName(enrollment.enrollmentType()), statusName(enrollment.enrollmentStatus()),
                        TIME.format(enrollment.enrolledAt())});
            }
            summary.setText("共 " + enrollments.size() + " 条");
            showState(enrollments.isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    enrollments.isEmpty() ? "当前学期还没有选课，可前往“选课”选择课程" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { refresh(); }

    private CompletableFuture<List<EnrollmentRow>> loadRows() {
        CompletableFuture<List<EnrollmentView>> enrollmentsRequest = gateway.currentEnrollments();
        CompletableFuture<List<OfferingSummary>> offeringsRequest = gateway.currentTermId()
                .thenCompose(termId -> loadOfferingPage(termId, 0, new ArrayList<>()));
        return enrollmentsRequest.thenCombine(offeringsRequest, EnrollmentPayload::new)
                .thenApply(MyEnrollmentPanel::resolveRows);
    }

    private CompletableFuture<List<OfferingSummary>> loadOfferingPage(
            String termId, int pageNumber, List<OfferingSummary> collected) {
        return gateway.searchOfferings(new OfferingSearchQuery(
                termId, "", null, false, pageNumber, 100)).thenCompose(page -> {
            collected.addAll(page.items());
            if ((long) (pageNumber + 1) * page.pageSize() >= page.total()) {
                return CompletableFuture.completedFuture(List.copyOf(collected));
            }
            return loadOfferingPage(termId, pageNumber + 1, collected);
        });
    }

    private static List<EnrollmentRow> resolveRows(EnrollmentPayload payload) {
        Map<String, OfferingSummary> offerings = new HashMap<>();
        payload.offerings().forEach(offering -> offerings.put(offering.offeringId(), offering));
        return payload.enrollments().stream()
                .map(enrollment -> enrollmentRow(enrollment, offerings)).toList();
    }

    private static EnrollmentRow enrollmentRow(EnrollmentView enrollment,
            Map<String, OfferingSummary> offerings) {
        OfferingSummary offering = offerings.get(enrollment.offeringId());
        if (offering == null) return new EnrollmentRow(enrollment, "未知课程", "未知教师");
        return new EnrollmentRow(enrollment, offering.courseName(), offering.teacherName());
    }

    private static String typeName(String type) {
        return "RETAKE".equals(type) ? "重修" : "NORMAL".equals(type) ? "正常选课" : type;
    }

    private static String statusName(String status) {
        return "ACTIVE".equals(status) ? "有效" : "DROPPED".equals(status) ? "已退选" : status;
    }

    private record EnrollmentPayload(List<EnrollmentView> enrollments,
                                     List<OfferingSummary> offerings) { }
    private record EnrollmentRow(EnrollmentView enrollment, String courseName,
                                 String teacherName) { }
}
