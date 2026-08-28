package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/** Administrator catalog page backed by the live paged catalog query. */
public final class CourseCatalogPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextField keyword = new JTextField();
    private final JCheckBox activeOnly = new JCheckBox("仅显示启用课程");
    private final DefaultTableModel model = readOnlyModel("课程代码", "课程名称", "学分", "总学时", "状态", "版本");

    public CourseCatalogPanel(CourseUiGateway gateway) {
        super("课程目录管理", "维护课程代码、名称、学分与启用状态；修改使用服务端乐观锁版本。");
        this.gateway = gateway;
        body.add(filters(), BorderLayout.NORTH);
        JTable table = table(new Object[0][0], new Object[0]);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("课程目录列表");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        body.add(scroll, BorderLayout.CENTER);
        search();
    }

    private JPanel filters() {
        JPanel panel = new JPanel();
        panel.setBackground(UiColors.BACKGROUND_SUBTLE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(UiSpacing.LG, UiSpacing.LG, UiSpacing.LG, UiSpacing.LG));
        keyword.setPreferredSize(new Dimension(280, UiDimensions.CONTROL_HEIGHT));
        keyword.setMaximumSize(new Dimension(360, UiDimensions.CONTROL_HEIGHT));
        keyword.setFont(UiTypography.BODY);
        keyword.getAccessibleContext().setAccessibleName("课程代码或名称");
        keyword.setToolTipText("输入课程代码或名称");
        panel.add(label("课程关键字", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(keyword);
        panel.add(Box.createHorizontalStrut(UiSpacing.LG));
        activeOnly.setOpaque(false);
        activeOnly.setFont(UiTypography.BODY);
        panel.add(activeOnly);
        panel.add(Box.createHorizontalGlue());
        JButton reset = secondary("重置条件");
        reset.addActionListener(event -> { keyword.setText(""); activeOnly.setSelected(false); search(); });
        panel.add(reset);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton search = primary("查询课程");
        search.addActionListener(event -> search());
        panel.add(search);
        return panel;
    }

    private void search() {
        showState(ViewState.LOADING, "正在查询课程目录，请稍候");
        CourseCatalogQuery query = new CourseCatalogQuery(keyword.getText().trim(), activeOnly.isSelected() ? Boolean.TRUE : null, 0, 50);
        gateway.searchCatalog(query).whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            if (error != null) { showState(ViewState.DISCONNECTED, "无法读取课程目录，请检查连接后重试"); return; }
            for (CourseView row : page.items()) model.addRow(new Object[]{
                    row.courseCode(), row.courseName(), row.credit().stripTrailingZeros().toPlainString(), row.totalHours(),
                    row.active() ? "启用" : "已停用", "v" + row.rowVersion()});
            showState(page.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    page.items().isEmpty() ? "未找到符合条件的课程，请调整查询条件" : "");
        }));
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
}
