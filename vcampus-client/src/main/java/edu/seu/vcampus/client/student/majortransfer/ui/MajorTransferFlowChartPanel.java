package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.majortransfer.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Visual stepper / process pipeline component for major transfer applications.
 * Synchronizes with real-time status and displays existing stages:
 * 1. 学生网上申请
 * 2. 转出院系审核
 * 3. 转入学院审核
 * 4. 考核与成绩录入
 * 5. 拟录取
 * 6. 终审
 * 7. 转专业完成
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
            "拟录取",
            "终审",
            "转专业完成"
    };

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final JLabel majorTitleLabel = new JLabel("转专业申请流程");
    private final JLabel globalStatusBadge = new JLabel(" 未开始 ");
    private final JLabel deptLabel = new JLabel("申请院系: --");
    private final JLabel timeLabel = new JLabel("上次更新时间: --");
    private final StepperCanvas canvas = new StepperCanvas();

    private final StepInfo[] steps = new StepInfo[STAGE_NAMES.length];

    public MajorTransferFlowChartPanel() {
        super(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 230, 235), 1, true),
                new EmptyBorder(16, 20, 16, 20)));

        for (int i = 0; i < steps.length; i++) {
            steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.PENDING, "未开始");
        }

        buildHeader();
        add(canvas, BorderLayout.CENTER);
        setPreferredSize(new Dimension(0, 195));
        setMinimumSize(new Dimension(500, 190));
    }

    private void buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 6));
        header.setOpaque(false);

        JPanel leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBox.setOpaque(false);

        JLabel bigNumber = new JLabel("1");
        bigNumber.setFont(new Font("SansSerif", Font.BOLD, 32));
        bigNumber.setForeground(new Color(180, 205, 237));
        leftBox.add(bigNumber);

        JPanel titleAndMeta = new JPanel();
        titleAndMeta.setOpaque(false);
        titleAndMeta.setLayout(new BoxLayout(titleAndMeta, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        majorTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        majorTitleLabel.setForeground(UiColors.TEXT_PRIMARY);
        titleRow.add(majorTitleLabel);

        globalStatusBadge.setFont(UiTypography.CAPTION.deriveFont(Font.BOLD));
        globalStatusBadge.setOpaque(true);
        globalStatusBadge.setBackground(StepStatus.PENDING.bg);
        globalStatusBadge.setForeground(StepStatus.PENDING.color);
        globalStatusBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(StepStatus.PENDING.color, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        titleRow.add(globalStatusBadge);
        titleAndMeta.add(titleRow);

        titleAndMeta.add(Box.createVerticalStrut(4));

        JPanel metaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        metaRow.setOpaque(false);
        deptLabel.setFont(UiTypography.CAPTION);
        deptLabel.setForeground(UiColors.TEXT_SECONDARY);
        timeLabel.setFont(UiTypography.CAPTION);
        timeLabel.setForeground(UiColors.TEXT_SECONDARY);
        metaRow.add(deptLabel);
        metaRow.add(timeLabel);
        titleAndMeta.add(metaRow);

        leftBox.add(titleAndMeta);
        header.add(leftBox, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);
    }

    /** Update flow chart data from the current application view and options. */
    public void update(MajorTransferApplicationView app, List<MajorTransferOptionView> options) {
        if (app == null) {
            majorTitleLabel.setText("尚未提交转专业申请");
            setGlobalBadge("未申请", StepStatus.PENDING);
            deptLabel.setText("申请院系: --");
            timeLabel.setText("上次更新时间: --");
            for (int i = 0; i < steps.length; i++) {
                steps[i] = new StepInfo(STAGE_NAMES[i], i == 0 ? StepStatus.IN_PROGRESS : StepStatus.PENDING,
                        i == 0 ? "可填写信息并提交申请" : "待提交申请");
            }
            canvas.repaint();
            return;
        }

        // Target major & dept info
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
        majorTitleLabel.setText(targetMajor);
        deptLabel.setText("申请院系: " + targetDept);
        timeLabel.setText("上次更新时间: " + (app.updatedAt() != null ? TIME_FMT.format(app.updatedAt()) : "--"));

        // Status mapping to 7 steps
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
                if (i < 6) steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
                else steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.FAILED, "执行失败，待管理员处理");
            }
        } else if (status == MajorTransferStatus.EFFECTIVE) {
            setGlobalBadge("已完成", StepStatus.COMPLETED);
            for (int i = 0; i < steps.length; i++) {
                steps[i] = new StepInfo(STAGE_NAMES[i], StepStatus.COMPLETED, "已完成");
            }
        } else {
            // Normal in-progress or draft
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
        globalStatusBadge.setText(" " + text + " ");
        globalStatusBadge.setBackground(status.bg);
        globalStatusBadge.setForeground(status.color);
        globalStatusBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(status.color, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
    }

    private static int mapStatusToActiveIndex(MajorTransferStatus status) {
        return switch (status) {
            case DRAFT -> 0;
            case SUBMITTED -> 1;
            case SOURCE_APPROVED -> 2;
            case QUALIFIED -> 3;
            case ASSESSED -> 4;
            case PROPOSED -> 5;
            case PENDING_EFFECTIVE -> 6;
            case EFFECTIVE -> 6;
            case REJECTED, CANCELLED, EXECUTION_FAILED -> 1;
        };
    }

    private static int findRejectedStageIndex(MajorTransferApplicationView app) {
        if (app.reviews() != null) {
            for (var rev : app.reviews()) {
                if (rev.decision() == MajorTransferDecision.REJECT) {
                    return switch (rev.reviewStage()) {
                        case SOURCE_REVIEW -> 1;
                        case QUALIFICATION_REVIEW -> 2;
                        case ASSESSMENT -> 3;
                        case PROPOSAL -> 4;
                        case FINAL_APPROVAL -> 5;
                        case EXECUTION -> 6;
                    };
                }
            }
        }
        return 1;
    }

    private static String getRejectionComment(MajorTransferApplicationView app) {
        if (app.reviews() != null) {
            for (var rev : app.reviews()) {
                if (rev.decision() == MajorTransferDecision.REJECT && rev.comment() != null && !rev.comment().isBlank()) {
                    return rev.comment().trim();
                }
            }
        }
        return null;
    }

    /** Canvas component drawing the stepper pipeline. */
    private final class StepperCanvas extends JComponent {
        private static final int NODE_DIAMETER = 28;
        private static final int NODE_Y = 22;

        public StepperCanvas() {
            setOpaque(false);
            setToolTipText(""); // Enable tooltip events
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int stepIndex = getStepAt(event.getX());
            if (stepIndex >= 0 && stepIndex < steps.length) {
                StepInfo step = steps[stepIndex];
                return (stepIndex + 1) + ". " + step.name + " (" + step.status.text + "): " + step.detail;
            }
            return null;
        }

        private int getStepAt(int x) {
            int w = getWidth();
            if (w <= 0) return -1;
            int margin = 35;
            int count = steps.length;
            double stepGap = (double) (w - 2 * margin) / (count - 1);
            for (int i = 0; i < count; i++) {
                int cx = (int) Math.round(margin + i * stepGap);
                if (Math.abs(x - cx) <= 30) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int margin = 35;
            int count = steps.length;
            double stepGap = (double) (width - 2 * margin) / (count - 1);

            // 1. Draw connecting lines
            int lineY = NODE_Y + NODE_DIAMETER / 2;
            for (int i = 0; i < count - 1; i++) {
                int x1 = (int) Math.round(margin + i * stepGap) + NODE_DIAMETER / 2;
                int x2 = (int) Math.round(margin + (i + 1) * stepGap) - NODE_DIAMETER / 2;

                StepInfo current = steps[i];
                StepInfo next = steps[i + 1];
                Color lineColor;
                if (current.status == StepStatus.COMPLETED) {
                    if (next.status == StepStatus.COMPLETED || next.status == StepStatus.IN_PROGRESS) {
                        lineColor = new Color(76, 175, 80);
                    } else if (next.status == StepStatus.REJECTED) {
                        lineColor = new Color(229, 57, 53);
                    } else {
                        lineColor = new Color(210, 215, 220);
                    }
                } else if (current.status == StepStatus.REJECTED) {
                    lineColor = new Color(229, 57, 53);
                } else {
                    lineColor = new Color(210, 215, 220);
                }

                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(x1, lineY, x2, lineY);
            }

            // 2. Draw nodes, labels, and status badges
            Font numFont = new Font("SansSerif", Font.BOLD, 12);
            Font labelFont = new Font("SansSerif", Font.PLAIN, 12);
            Font badgeFont = new Font("SansSerif", Font.BOLD, 10);

            for (int i = 0; i < count; i++) {
                int cx = (int) Math.round(margin + i * stepGap);
                StepInfo step = steps[i];

                // Node circle
                int circleX = cx - NODE_DIAMETER / 2;
                int circleY = NODE_Y;

                g2.setColor(step.status.color);
                g2.fillOval(circleX, circleY, NODE_DIAMETER, NODE_DIAMETER);

                // If in progress, draw a subtle outer glowing ring
                if (step.status == StepStatus.IN_PROGRESS) {
                    g2.setColor(new Color(step.status.color.getRed(), step.status.color.getGreen(), step.status.color.getBlue(), 60));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawOval(circleX - 3, circleY - 3, NODE_DIAMETER + 6, NODE_DIAMETER + 6);
                }

                // Node number
                g2.setFont(numFont);
                g2.setColor(Color.WHITE);
                String numStr = String.valueOf(i + 1);
                FontMetrics fmNum = g2.getFontMetrics();
                int numW = fmNum.stringWidth(numStr);
                int numH = fmNum.getAscent();
                g2.drawString(numStr, cx - numW / 2, circleY + (NODE_DIAMETER + numH) / 2 - 2);

                // Stage title text
                g2.setFont(labelFont);
                g2.setColor(new Color(51, 51, 51));
                FontMetrics fmLabel = g2.getFontMetrics();
                int labelW = fmLabel.stringWidth(step.name);
                g2.drawString(step.name, cx - labelW / 2, circleY + NODE_DIAMETER + 20);

                // Status pill badge
                int badgeW = 52;
                int badgeH = 20;
                int badgeX = cx - badgeW / 2;
                int badgeY = circleY + NODE_DIAMETER + 28;

                g2.setColor(step.status.color);
                g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 6, 6);

                g2.setFont(badgeFont);
                g2.setColor(Color.WHITE);
                FontMetrics fmBadge = g2.getFontMetrics();
                int bTextW = fmBadge.stringWidth(step.status.text);
                int bTextH = fmBadge.getAscent();
                g2.drawString(step.status.text, cx - bTextW / 2, badgeY + (badgeH + bTextH) / 2 - 2);
            }

            g2.dispose();
        }
    }
}
