package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;

/** Shared course-page composition that consumes the teammate-owned UI tokens. */
abstract class AbstractCoursePanel extends JPanel {
    protected final JPanel body = new JPanel(new BorderLayout(0, UiSpacing.LG));

    protected AbstractCoursePanel(String title, String description) {
        super(new BorderLayout(0, UiSpacing.XL));
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(BorderFactory.createEmptyBorder(UiSpacing.PAGE_PADDING, UiSpacing.PAGE_PADDING,
                UiSpacing.PAGE_PADDING, UiSpacing.PAGE_PADDING));
        body.setOpaque(false);
        add(heading(title, description), BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    private static JPanel heading(String title, String description) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(label("课程中心  /  " + title, UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label(title, UiTypography.PAGE_TITLE, UiColors.TEXT_PRIMARY));
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label(description, UiTypography.BODY, UiColors.TEXT_SECONDARY));
        return panel;
    }

    protected static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    protected static JButton primary(String text) {
        JButton button = button(text);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(UiColors.ACCENT);
        button.setForeground(UiColors.TEXT_ON_PRIMARY);
        button.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.LG, 0, UiSpacing.LG));
        return button;
    }

    protected static JButton secondary(String text) {
        JButton button = button(text);
        button.setBackground(UiColors.BACKGROUND_PAGE);
        button.setForeground(UiColors.PRIMARY);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiColors.PRIMARY),
                BorderFactory.createEmptyBorder(0, UiSpacing.LG, 0, UiSpacing.LG)));
        return button;
    }

    private static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(UiTypography.BODY_BOLD);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, UiDimensions.CONTROL_HEIGHT));
        button.setMargin(new Insets(0, UiSpacing.LG, 0, UiSpacing.LG));
        button.setFocusPainted(true);
        button.getAccessibleContext().setAccessibleName(text);
        return button;
    }

    protected static JTable table(Object[][] rows, Object[] columns) {
        JTable table = new JTable(rows, columns);
        table.setFont(UiTypography.BODY);
        table.setForeground(UiColors.TEXT_PRIMARY);
        table.setBackground(UiColors.BACKGROUND_PAGE);
        table.setRowHeight(UiDimensions.TABLE_ROW_HEIGHT);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(UiColors.BORDER_DEFAULT);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setFont(UiTypography.BODY_BOLD);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getTableHeader().setForeground(UiColors.TEXT_PRIMARY);
        table.getTableHeader().setPreferredSize(new Dimension(0, UiDimensions.TABLE_ROW_HEIGHT));
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setBorder(BorderFactory.createEmptyBorder(0, UiSpacing.SM, 0, UiSpacing.SM));
        table.setDefaultRenderer(Object.class, renderer);
        table.getAccessibleContext().setAccessibleName("课程数据表格");
        return table;
    }
}
