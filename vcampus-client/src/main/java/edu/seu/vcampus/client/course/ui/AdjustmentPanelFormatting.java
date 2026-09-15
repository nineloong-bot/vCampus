package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.TermPhaseView;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;

/** Section layout and display formatting for the adjustment panel segments. */
abstract class AdjustmentPanelFormatting extends AdjustmentPanelBase {

    AdjustmentPanelFormatting(CourseUiGateway gateway) {
        super(gateway);
    }

    AdjustmentPanelFormatting(CourseUiGateway gateway, ChangeConfirmation confirmation) {
        super(gateway, confirmation);
    }

    static JPanel section(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, UiSpacing.SM));
        panel.setOpaque(false);
        panel.add(label(title, UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    static String phaseText(TermPhaseView phase) {
        String status = switch (phase.phase()) {
            case "ENROLLMENT" -> "正常选课开放";
            case "ADJUSTMENT" -> "退改补开放";
            default -> "只读阶段";
        };
        if ("CLOSED".equals(phase.termStatus())) status = "学期已关闭";
        return "服务端阶段：" + status + "    退改补时间窗：" + TIME.format(phase.adjustmentStartAt())
                + " 至 " + TIME.format(phase.adjustmentEndAt()) + "    服务器时间：" + TIME.format(phase.serverTime());
    }

    OfferingSummary findOffering(String offeringId) {
        return offerings.stream().filter(row -> row.offeringId().equals(offeringId)).findFirst().orElse(null);
    }

    static String enrollmentType(String type) {
        return switch (type) {
            case "NORMAL" -> "正常选课";
            case "LATE_ADD" -> "补选";
            case "RETAKE" -> "重修";
            default -> "其他";
        };
    }

    static String enrollmentStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "有效";
            case "DROPPED" -> "已退选";
            default -> "未知";
        };
    }

    static boolean overlaps(ScheduleItem left, ScheduleItem right) {
        return left.dayOfWeek().equals(right.dayOfWeek())
                && left.startWeek() <= right.endWeek() && right.startWeek() <= left.endWeek()
                && left.startPeriod() <= right.endPeriod() && right.startPeriod() <= left.endPeriod();
    }
}
