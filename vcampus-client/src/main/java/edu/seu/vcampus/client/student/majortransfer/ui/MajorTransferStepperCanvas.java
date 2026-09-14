package edu.seu.vcampus.client.student.majortransfer.ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

final class MajorTransferStepperCanvas extends JComponent {
    private static final int NODE_DIAMETER = 28;
    private static final int NODE_Y = 22;
    private static final int MARGIN = 35;
    private final Supplier<MajorTransferFlowChartPanel.StepInfo[]> stepsSupplier;

    MajorTransferStepperCanvas(Supplier<MajorTransferFlowChartPanel.StepInfo[]> stepsSupplier) {
        this.stepsSupplier = stepsSupplier;
        setOpaque(false);
        setToolTipText("");
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        var steps = stepsSupplier.get();
        int stepIndex = getStepAt(event.getX(), steps.length);
        if (stepIndex < 0 || stepIndex >= steps.length) {
            return null;
        }
        var step = steps[stepIndex];
        return (stepIndex + 1) + ". " + step.name() + " (" + step.status().text + "): " + step.detail();
    }

    private int getStepAt(int x, int count) {
        if (getWidth() <= 0) {
            return -1;
        }
        double gap = gap(count);
        for (int i = 0; i < count; i++) {
            if (Math.abs(x - center(i, gap)) <= 30) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        var g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var steps = stepsSupplier.get();
        double gap = gap(steps.length);
        drawConnections(g, steps, gap);
        drawNodes(g, steps, gap);
        g.dispose();
    }

    private double gap(int count) {
        return (double) (getWidth() - 2 * MARGIN) / (count - 1);
    }

    private static int center(int index, double gap) {
        return (int) Math.round(MARGIN + index * gap);
    }

    private static void drawConnections(Graphics2D g, MajorTransferFlowChartPanel.StepInfo[] steps,
                                        double gap) {
        int lineY = NODE_Y + NODE_DIAMETER / 2;
        for (int i = 0; i < steps.length - 1; i++) {
            g.setColor(connectionColor(steps[i].status(), steps[i + 1].status()));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(center(i, gap) + NODE_DIAMETER / 2, lineY,
                    center(i + 1, gap) - NODE_DIAMETER / 2, lineY);
        }
    }

    private static Color connectionColor(MajorTransferFlowChartPanel.StepStatus current,
                                         MajorTransferFlowChartPanel.StepStatus next) {
        if (current == MajorTransferFlowChartPanel.StepStatus.REJECTED
                || next == MajorTransferFlowChartPanel.StepStatus.REJECTED) {
            return new Color(229, 57, 53);
        }
        if (current == MajorTransferFlowChartPanel.StepStatus.COMPLETED
                && (next == MajorTransferFlowChartPanel.StepStatus.COMPLETED
                || next == MajorTransferFlowChartPanel.StepStatus.IN_PROGRESS)) {
            return new Color(76, 175, 80);
        }
        return new Color(210, 215, 220);
    }

    private static void drawNodes(Graphics2D g, MajorTransferFlowChartPanel.StepInfo[] steps, double gap) {
        for (int i = 0; i < steps.length; i++) {
            int center = center(i, gap);
            drawNode(g, steps[i], i, center);
        }
    }

    private static void drawNode(Graphics2D g, MajorTransferFlowChartPanel.StepInfo step,
                                 int index, int center) {
        int circleX = center - NODE_DIAMETER / 2;
        g.setColor(step.status().color);
        g.fillOval(circleX, NODE_Y, NODE_DIAMETER, NODE_DIAMETER);
        if (step.status() == MajorTransferFlowChartPanel.StepStatus.IN_PROGRESS) {
            var color = step.status().color;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(circleX - 3, NODE_Y - 3, NODE_DIAMETER + 6, NODE_DIAMETER + 6);
        }
        drawCentered(g, String.valueOf(index + 1), center, NODE_Y + 19,
                new Font("SansSerif", Font.BOLD, 12), Color.WHITE);
        drawCentered(g, step.name(), center, NODE_Y + NODE_DIAMETER + 20,
                new Font("SansSerif", Font.PLAIN, 12), new Color(51, 51, 51));
        drawBadge(g, step, center, NODE_Y + NODE_DIAMETER + 28);
    }

    private static void drawBadge(Graphics2D g, MajorTransferFlowChartPanel.StepInfo step,
                                  int center, int y) {
        g.setColor(step.status().color);
        g.fillRoundRect(center - 26, y, 52, 20, 6, 6);
        drawCentered(g, step.status().text, center, y + 14,
                new Font("SansSerif", Font.BOLD, 10), Color.WHITE);
    }

    private static void drawCentered(Graphics2D g, String text, int center, int baseline,
                                     Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, center - g.getFontMetrics().stringWidth(text) / 2, baseline);
    }
}
