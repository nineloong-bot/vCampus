package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** User-module password input with an independent, accessible eye-icon toggle. */
final class PasswordFieldWithVisibilityToggle extends JPanel {
    private static final char PASSWORD_BULLET = '\u2022';
    private static final Icon CLOSED_EYE = new EyeIcon(false);
    private static final Icon OPEN_EYE = new EyeIcon(true);

    private final JPasswordField field;
    private final JButton toggle = new JButton();
    private boolean visible;

    PasswordFieldWithVisibilityToggle(JPasswordField field) {
        super(new BorderLayout());
        this.field = field;
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(UiBorders.LINE);
        field.setEchoChar(PASSWORD_BULLET);
        field.setForeground(UiColors.TEXT_PRIMARY);
        field.setBorder(BorderFactory.createEmptyBorder(
                0, UiSpacing.SPACE_2, 0, UiSpacing.SPACE_1));
        configureToggle();
        add(field, BorderLayout.CENTER);
        add(toggle, BorderLayout.EAST);
        updateVisibilityState();
    }

    private void configureToggle() {
        toggle.setName(field.getName() + ".visibility");
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setFocusPainted(false);
        toggle.setFocusable(true);
        toggle.setBorder(BorderFactory.createEmptyBorder(
                0, UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2));
        toggle.addActionListener(event -> {
            visible = !visible;
            updateVisibilityState();
        });
    }

    private void updateVisibilityState() {
        field.setEchoChar(visible ? (char) 0 : PASSWORD_BULLET);
        String action = visible ? "隐藏密码" : "显示密码";
        toggle.setIcon(visible ? OPEN_EYE : CLOSED_EYE);
        toggle.setToolTipText(action);
        toggle.getAccessibleContext().setAccessibleName(action);
    }

    private static final class EyeIcon implements Icon {
        private static final int WIDTH = 20;
        private static final int HEIGHT = 16;
        private final boolean open;

        private EyeIcon(boolean open) {
            this.open = open;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D drawing = (Graphics2D) graphics.create();
            try {
                drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                drawing.setColor(component.isEnabled()
                        ? UiColors.TEXT_SECONDARY : UiColors.DISABLED_FG);
                drawing.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                if (open) paintOpenEye(drawing, x, y);
                else paintClosedEye(drawing, x, y);
            } finally {
                drawing.dispose();
            }
        }

        private static void paintOpenEye(Graphics2D drawing, int x, int y) {
            drawing.drawArc(x + 2, y + 3, 16, 10, 18, 144);
            drawing.drawArc(x + 2, y + 3, 16, 10, 198, 144);
            drawing.fillOval(x + 8, y + 6, 4, 4);
        }

        private static void paintClosedEye(Graphics2D drawing, int x, int y) {
            drawing.drawArc(x + 2, y + 3, 16, 9, 200, 140);
            drawing.drawLine(x + 4, y + 11, x + 2, y + 13);
            drawing.drawLine(x + 10, y + 12, x + 10, y + 15);
            drawing.drawLine(x + 16, y + 11, x + 18, y + 13);
        }

        @Override
        public int getIconWidth() {
            return WIDTH;
        }

        @Override
        public int getIconHeight() {
            return HEIGHT;
        }
    }
}
