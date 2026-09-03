package edu.seu.vcampus.client.shop.ui.style;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.Dimension;
import java.awt.LayoutManager;

/** Shared visual helpers for Shop pages. */
public final class ShopComponentStyle {
    private ShopComponentStyle() { }

    public static JPanel pagePanel(LayoutManager layout) {
        return pagePanel(new JPanel(layout));
    }

    public static <T extends JPanel> T pagePanel(T panel) {
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        return panel;
    }

    public static void styleTable(JTable table, boolean compact) {
        int rowHeight = compact ? 34 : 40;
        table.setRowHeight(rowHeight);
        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(0, rowHeight));
        header.setBackground(UiColors.BACKGROUND_SUBTLE);
        header.setForeground(UiColors.TEXT_SECONDARY);
        header.setFont(UiTypography.BODY_BOLD);

        table.setFont(UiTypography.BODY);
        table.setForeground(UiColors.TEXT_PRIMARY);
        table.setGridColor(UiColors.BORDER_DEFAULT);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(10, 0));
        table.setDragEnabled(false);
        table.setRowSelectionAllowed(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(UiColors.BACKGROUND_SUBTLE);
        table.setSelectionForeground(UiColors.TEXT_PRIMARY);
        table.getTableHeader().setReorderingAllowed(false);

        for (int i = 0; i < table.getColumnCount(); i++) {
            int align = inferAlignment(table.getColumnName(i));
            table.getColumnModel().getColumn(i).setCellRenderer(new ShopCellRenderer(align));
        }
    }

    public static <T extends JComponent> T styleTextComponent(T component) {
        component.setFont(UiTypography.BODY);
        component.setForeground(UiColors.TEXT_PRIMARY);
        component.setBackground(UiColors.BACKGROUND_PAGE);
        component.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        return component;
    }

    public static void styleTabbedPane(JTabbedPane tabs) {
        tabs.setFont(UiTypography.BODY);
        tabs.setForeground(UiColors.TEXT_PRIMARY);
        tabs.setBackground(UiColors.BACKGROUND_SUBTLE);
    }

    public static JScrollPane styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBorder(UiBorders.LINE);
        scrollPane.getViewport().setBackground(UiColors.BACKGROUND_PAGE);
        return scrollPane;
    }

    public static <T extends JPanel> T styleDialogContent(T panel) {
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        return panel;
    }

    /** Creates a shared-token section border for titled management groups. */
    public static javax.swing.border.Border sectionBorder(String title) {
        return BorderFactory.createTitledBorder(UiBorders.LINE, title);
    }

    private static int inferAlignment(String columnName) {
        String name = columnName == null ? "" : columnName;
        if (name.contains("金额") || name.contains("数量") || name.contains("价格")
                || name.contains("Qty") || name.contains("price")) {
            return javax.swing.SwingConstants.RIGHT;
        }
        if (name.contains("状态") || name.contains("ID") || name.contains("编号")
                || name.contains("短") ) {
            return javax.swing.SwingConstants.CENTER;
        }
        return javax.swing.SwingConstants.LEFT;
    }

    private static final class ShopCellRenderer extends DefaultTableCellRenderer {
        private final int alignment;

        private ShopCellRenderer(int alignment) {
            this.alignment = alignment;
        }

        @Override
        public javax.swing.JComponent getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column) {
            DefaultTableCellRenderer component = (DefaultTableCellRenderer) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            component.setFont(UiTypography.BODY);
            component.setForeground(UiColors.TEXT_PRIMARY);
            component.setHorizontalAlignment(alignment);
            if (isSelected) {
                component.setBackground(UiColors.BACKGROUND_SUBTLE);
            } else {
                component.setBackground(UiColors.BACKGROUND_PAGE);
            }
            return component;
        }
    }
}
