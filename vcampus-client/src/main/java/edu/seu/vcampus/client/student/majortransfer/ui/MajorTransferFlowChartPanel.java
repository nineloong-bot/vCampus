package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Visual stepper / process pipeline component for major transfer applications.
 * Synchronizes with real-time status and displays existing stages:
 * 1. 学生网上申请
 * 2. 转出院系审核
 * 3. 转入学院审核
 * 4. 考核与成绩录入
 * 5. 终审
 * 6. 转专业完成
 */
public final class MajorTransferFlowChartPanel extends JPanel {
    public enum StepStatus {
        COMPLETED("已完成", new Color(76, 175, 80), new Color(232, 245, 233)),
        IN_PROGRESS("进行中", new Color(251, 140, 0), new Color(255, 243, 224)),
        PENDING("未开始", new Color(189, 189, 189), new Color(245, 245, 245)),
        REJECTED("已驳回", new Color(229, 57, 53), new Color(255, 235, 238)),
        CANCELLED("已取消", new Color(117, 117, 117), new Color(238, 238, 238)),
        FAILED("执行失败", new Color(211, 47, 47), new Color(255, 235, 238));

        public final String text;
        public final Color color;
        public final Color bg;

        StepStatus(String text, Color color, Color bg) {
            this.text = text;
            this.color = color;
            this.bg = bg;
        }
    }

    public record StepInfo(String name, StepStatus status, String detail) {}
    private static final String[] STAGE_NAMES = {
            "学生网上申请",
            "转出院系审核",
            "转入学院审核",
            "考核与成绩录入",
            "终审",
            "转专业完成"
    };

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final StepInfo[] steps = new StepInfo[STAGE_NAMES.length];
    private final MajorTransferFlowHeader header = new MajorTransferFlowHeader();
    private final MajorTransferStepperCanvas canvas = new MajorTransferStepperCanvas(() -> steps);

    public MajorTransferFlowChartPanel() {
        super(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 235), 1, true),
                new EmptyBorder(16, 20, 16, 20)));

        for (int i = 0; i < steps.length; i++) {
            steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.PENDING, "未开始");
        }

        add(header, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);
        setPreferredSize(new Dimension(0, 195));
        setMinimumSize(new Dimension(500, 190));
    }

    /** Update flow chart data from the current application view and options. */
    public void update(MajorTransferApplicationView app, List<MajorTransferOptionView> options) {
        if (app == null) {
            header.setTitle("尚未提交转专业申请");
            setGlobalBadge("未申请", StepStatus.PENDING);
            header.setMetadata("--", "--");
            for (int i = 0; i < steps.length; i++) {
                steps[i] = new StepInfo(STAGE_NAMES[i], i == 0 ? StepStatus.IN_PROGRESS : StepStatus.PENDING,
                        i == 0 ? "可填写信息并提交申请" : "待提交申请");
            }
            canvas.repaint();
            return;
        }

        String targetMajor = "目标专业";
        String targetDept = "未知院系";
        if (options != null) {
            for (var opt : options) {
                if (opt.optionId().equals(app.optionId())) {
                    targetMajor = opt.targetMajorName();
                    targetDept = opt.targetDepartmentName();
                    break;
                }
            }
        }
        header.setTitle(targetMajor);
        header.setMetadata(targetDept, app.updatedAt() != null ? TIME_FMT.format(app.updatedAt()) : "--");

        MajorTransferStatus status = app.status();
        int activeIndex = mapStatusToActiveIndex(status);

        if (status == MajorTransferStatus.REJECTED) {
            setGlobalBadge("已驳回", StepStatus.REJECTED);
            int rejectedIndex = findRejectedStageIndex(app);
            for (int i = 0; i < steps.length; i++) {
                if (i < rejectedIndex) {
                    steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
                } else if (i == rejectedIndex) {
                    String reason = getRejectionComment(app);
                    steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.REJECTED, reason != null ? "驳回原因: " + reason : "已驳回");
                } else {
                    steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.PENDING, "未开始");
                }
            }
        } else if (status == MajorTransferStatus.CANCELLED) {
            setGlobalBadge("已取消", StepStatus.CANCELLED);
            for (int i = 0; i < steps.length; i++) {
                if (i < activeIndex) steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
                else if (i == activeIndex) steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.CANCELLED, "已取消");
                else steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.PENDING, "未开始");
            }
        } else if (status == MajorTransferStatus.EXECUTION_FAILED) {
            setGlobalBadge("执行失败", StepStatus.FAILED);
            for (int i = 0; i < steps.length; i++) {
                if (i < 5) steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
                else steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.FAILED, "执行失败，待管理员处理");
            }
        } else if (status == MajorTransferStatus.EFFECTIVE) {
            setGlobalBadge("已完成", StepStatus.COMPLETED);
            for (int i = 0; i < steps.length; i++) {
                steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
            }
        } else {
            String badgeText = status == MajorTransferStatus.DRAFT ? "草稿" : "进行中";
            setGlobalBadge(badgeText, StepStatus.IN_PROGRESS);
            for (int i = 0; i < steps.length; i++) {
                if (i < activeIndex) {
                    steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
                } else if (i == activeIndex) {
                    steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.IN_PROGRESS, "正在进行中");
                } else {
                    steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.PENDING, "未开始");
                }
            }
        }

        canvas.repaint();
    }

    private void setGlobalBadge(String text, StepStatus status) {
        header.setBadge(text, status);
    }

    private static int mapStatusToActiveIndex(MajorTransferStatus status) {
        return switch (status) {
            case DRAFT -> 0;
            case SUBMITTED -> 1;
            case SOURCE_APPROVED -> 2;
            case QUALIFIED -> 3;
            case ASSESSED -> 4;
            case PENDING_EFFECTIVE -> 5;
            case EFFECTIVE -> 5;
            case REJECTED, CANCELLED, EXECUTION_FAILED -> 1;
        };
    }

    private static int findRejectedStageIndex(MajorTransferApplicationView app) {
        if (app.reviews() != null) {
            for (var rev : app.reviews()) {
                if (rev.decision() == MajorTransferDecision.REJECT
                        && rev.reviewStage() != MajorTransferReviewStage.FINAL_APPROVAL_ROLLBACK) {
                    return switch (rev.reviewStage()) {
                        case SOURCE_REVIEW -> 1;
                        case QUALIFICATION_REVIEW -> 2;
                        case ASSESSMENT -> 3;
                        case FINAL_APPROVAL -> 4;
                        case FINAL_APPROVAL_ROLLBACK -> 4;
                        case EXECUTION -> 5;
                    };
                }
            }
        }
        return 1;
    }

    private static String getRejectionComment(MajorTransferApplicationView app) {
        if (app.reviews() != null) {
            for (var rev : app.reviews()) {
                if (rev.decision() == MajorTransferDecision.REJECT
                        && rev.reviewStage() != MajorTransferReviewStage.FINAL_APPROVAL_ROLLBACK
                        && rev.comment() != null && !rev.comment().isBlank()) {
                    return rev.comment().trim();
                }
            }
        }
        return null;
    }

}
