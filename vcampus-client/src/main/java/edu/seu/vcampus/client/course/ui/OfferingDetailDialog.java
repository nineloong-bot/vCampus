package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.course.service.CourseClientException;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Standard 720 px offering detail/change-preview dialog. */
public final class OfferingDetailDialog extends JDialog {
    private final UiAsyncGuard asyncGuard = new UiAsyncGuard();
    public OfferingDetailDialog(Window owner, OfferingSummary source, OfferingSummary target,
                                String conflictResult, Runnable onConfirm) {
        this(owner, source, target, conflictResult, () -> {
            onConfirm.run();
            return CompletableFuture.completedFuture(null);
        }, () -> { });
    }

    OfferingDetailDialog(Window owner, OfferingSummary source, OfferingSummary target,
                         String conflictResult, Supplier<CompletableFuture<?>> request,
                         Runnable onSuccess) {
        super(owner, "改选确认", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.XL));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        JLabel title = new JLabel("改选教学班确认");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        root.add(title, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.LG));
        content.setOpaque(false);
        JPanel comparison = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.XL, 0));
        comparison.setOpaque(false);
        comparison.add(offeringSummary("原教学班", source, "确认后仅在目标班校验成功时退出"));
        comparison.add(offeringSummary("目标教学班", target, "服务端将再次校验容量、重复和冲突"));
        content.add(comparison, BorderLayout.CENTER);
        JLabel conflict = new JLabel(conflictResult);
        conflict.setFont(UiTypography.BODY_BOLD);
        conflict.setForeground(conflictResult.startsWith("未发现") ? UiColors.SUCCESS_FG : UiColors.ACCENT);
        conflict.setOpaque(true);
        conflict.setBackground(conflictResult.startsWith("未发现") ? UiColors.SUCCESS_BG : UiColors.BACKGROUND_SUBTLE);
        conflict.setBorder(BorderFactory.createEmptyBorder(UiSpacing.MD, UiSpacing.LG, UiSpacing.MD, UiSpacing.LG));
        content.add(conflict, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.MD, 0));
        actions.setOpaque(false);
        JLabel error = text(" ", UiTypography.BODY, UiColors.ACCENT);
        actions.add(error);
        var cancel = AbstractCoursePanel.secondary("取消改选");
        cancel.addActionListener(event -> dispose());
        actions.add(cancel);
        JButton confirm = AbstractCoursePanel.primary("确认改选");
        confirm.addActionListener(event -> submit(request, onSuccess, confirm, cancel, error));
        actions.add(confirm);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(confirm);
        setSize(720, 460);
        setLocationRelativeTo(owner);
    }

    private void submit(Supplier<CompletableFuture<?>> request, Runnable onSuccess,
                        JButton confirm, JButton cancel, JLabel error) {
        error.setText(" ");
        confirm.setEnabled(false);
        cancel.setEnabled(false);
        confirm.setText("正在改选…");
        CompletableFuture<?> pending;
        try {
            pending = request.get();
        } catch (RuntimeException failure) {
            pending = CompletableFuture.failedFuture(failure);
        }
        long asyncRequest = asyncGuard.begin();
        pending.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (!asyncGuard.accepts(asyncRequest)) return;
            confirm.setEnabled(true);
            cancel.setEnabled(true);
            confirm.setText("确认改选");
            if (failure != null) {
                error.setText(changeError(failure));
                return;
            }
            onSuccess.run();
            dispose();
        }));
    }

    @Override public void dispose() {
        asyncGuard.deactivate();
        super.dispose();
    }

    private static String changeError(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof CourseClientException clientFailure) {
            return switch (clientFailure.code()) {
                case "COURSE_OFFERING_FULL" -> "教学班容量已满，请选择其他教学班";
                case "COURSE_SCHEDULE_CONFLICT" -> "目标教学班与当前课表冲突，请调整选择";
                case "COMMON_CONCURRENT_MODIFICATION" -> "原选课记录已变化，请刷新后重试";
                default -> clientFailure.getMessage();
            };
        }
        return "改选失败，请检查连接后重试";
    }

    private static JPanel offeringSummary(String heading, OfferingSummary offering, String note) {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT),
                BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG)));
        panel.add(text(heading, UiTypography.SECTION_TITLE, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.MD));
        panel.add(text(offering.courseName() + " · " + offering.className(), UiTypography.BODY_BOLD, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM));
        panel.add(text("课程代码：" + offering.courseCode(), UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM));
        panel.add(text("时间：" + scheduleText(offering), UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.SM));
        panel.add(text("容量：" + offering.enrolledCount() + " / " + offering.capacity(), UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(javax.swing.Box.createVerticalStrut(UiSpacing.MD));
        panel.add(text(note, UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        return panel;
    }

    private static JLabel text(String value, java.awt.Font font, java.awt.Color color) {
        JLabel label = new JLabel(value);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static String scheduleText(OfferingSummary offering) {
        if (offering.schedules().isEmpty()) return "待安排";
        return offering.schedules().stream().map(OfferingDetailDialog::scheduleText).collect(Collectors.joining("；"));
    }

    private static String scheduleText(ScheduleItem item) {
        String day = switch (item.dayOfWeek()) {
            case "MONDAY" -> "周一"; case "TUESDAY" -> "周二"; case "WEDNESDAY" -> "周三";
            case "THURSDAY" -> "周四"; case "FRIDAY" -> "周五"; case "SATURDAY" -> "周六";
            case "SUNDAY" -> "周日"; default -> item.dayOfWeek();
        };
        return day + " 第" + item.startPeriod() + "–" + item.endPeriod() + "节 第"
                + item.startWeek() + "–" + item.endWeek() + "周 " + item.classroom();
    }
}
