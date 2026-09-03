package edu.seu.vcampus.client.shop.ui.style;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import org.junit.jupiter.api.Test;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;

import static org.assertj.core.api.Assertions.assertThat;

class ShopComponentStyleTest {
    @Test
    void stylePagePanelUsesSharedInsetsAndBackground() {
        JPanel page = ShopComponentStyle.pagePanel(new BorderLayout());
        assertThat(page.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(page.getBorder().getBorderInsets(page))
                .isEqualTo(UiBorders.pageInset().getBorderInsets(page));
    }

    @Test
    void styleTabbedPaneUsesSharedTypographyAndColors() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("tab1", new JLabel("a"));
        tabs.addTab("tab2", new JLabel("b"));
        ShopComponentStyle.styleTabbedPane(tabs);

        assertThat(tabs.getFont()).isEqualTo(UiTypography.BODY);
        assertThat(tabs.getForeground()).isEqualTo(UiColors.TEXT_PRIMARY);
    }

    @Test
    void styleTextComponentAppliesSharedLookAndFeel() {
        JTextField textField = ShopComponentStyle.styleTextComponent(new JTextField("value"));
        JTextArea textArea = ShopComponentStyle.styleTextComponent(new JTextArea("line"));

        assertThat(textField.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(textArea.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(textField.getForeground()).isEqualTo(UiColors.TEXT_PRIMARY);
        assertThat(textArea.getForeground()).isEqualTo(UiColors.TEXT_PRIMARY);
        assertThat(textField.getFont()).isEqualTo(UiTypography.BODY);
        assertThat(textArea.getFont()).isEqualTo(UiTypography.BODY);
        assertThat(((JComponent) textField).getBorder()).isNotNull();
        assertThat(((JComponent) textArea).getBorder()).isNotNull();
    }

    @Test
    void styleScrollPaneKeepsSharedViewportAndBorder() {
        JPanel content = new JPanel(new FlowLayout());
        content.add(new JLabel("content"));
        JScrollPane scrollPane = new JScrollPane(content);
        ShopComponentStyle.styleScrollPane(scrollPane);

        assertThat(scrollPane.getViewport().getBackground())
                .isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(scrollPane.getViewport().getView()).isSameAs(content);
        assertThat(scrollPane.getBorder()).isNotNull();
    }

    @Test
    void styleDialogContentUsesSharedInsetsAndButtonSpacing() {
        JPanel panel = ShopComponentStyle.styleDialogContent(new JPanel());
        assertThat(panel.getBackground()).isEqualTo(UiColors.BACKGROUND_PAGE);
        assertThat(panel.getBorder().getBorderInsets(panel))
                .isEqualTo(UiBorders.pageInset().getBorderInsets(panel));
    }

    @Test
    void styleTableSetsDensityAlignmentAndSelectionInvariants() {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{{"商品A", "12.00", "待发货", "ABC123"},
                {"商品B", "8.20", "已完成", "DEF456"}}, new Object[]{"名称", "金额", "状态", "ID"});
        JTable table = new JTable(model);

        ShopComponentStyle.styleTable(table, false);
        assertThat(table.getRowHeight()).isEqualTo(40);
        assertThat(table.getTableHeader().getPreferredSize().height).isEqualTo(40);
        assertThat(table.getSelectionModel().getSelectionMode())
                .isEqualTo(ListSelectionModel.SINGLE_SELECTION);
        assertThat(table.getTableHeader().getReorderingAllowed()).isFalse();
        assertThat(table.getShowHorizontalLines()).isTrue();
        assertThat(table.getShowVerticalLines()).isFalse();

        for (int column = 0; column < table.getColumnCount(); column++) {
            TableCellRenderer renderer = table.getCellRenderer(0, column);
            Component rendered = renderer.getTableCellRendererComponent(table, table.getValueAt(0, column), false,
                    false, 0, column);
            if (rendered instanceof JLabel label) {
                int align = label.getHorizontalAlignment();
                if (column == 0) {
                    assertThat(align).isEqualTo(JLabel.LEFT);
                }
                if (column == 1) {
                    assertThat(align).isEqualTo(JLabel.RIGHT);
                }
                if (column == 2 || column == 3) {
                    assertThat(align).isEqualTo(JLabel.CENTER);
                }
            }
        }

        ShopComponentStyle.styleTable(table, true);
        assertThat(table.getRowHeight()).isEqualTo(34);
        assertThat(table.getTableHeader().getPreferredSize().height).isEqualTo(34);
    }
}
