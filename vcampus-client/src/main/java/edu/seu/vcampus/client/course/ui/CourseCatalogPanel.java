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
import java.util.ArrayList;
import java.util.List;

/** Administrator catalog page backed by the live paged catalog query. */
public final class CourseCatalogPanel extends AbstractCoursePanel {
    private final CourseUiGateway gateway;
    private final JTextField keyword = new JTextField();
    private final JCheckBox activeOnly = new JCheckBox("仅显示启用课程");
    private final DefaultTableModel model = readOnlyModel("课程代码", "课程名称", "学分", "总学时", "状态", "版本");
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final List<CourseView> courses = new ArrayList<>();

    public CourseCatalogPanel(CourseUiGateway gateway) {
        super("课程目录管理", "维护课程代码、名称、学分与启用状态；修改使用服务端乐观锁版本。");
        this.gateway = gateway;
        body.add(filters(), BorderLayout.NORTH);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("课程目录列表");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        body.add(scroll, BorderLayout.CENTER);
        body.add(actions(), BorderLayout.SOUTH);
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
        JButton search = secondary("查询课程");
        search.addActionListener(event -> search());
        panel.add(search);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(label("新增和短编辑使用统一课程表单，保存后自动刷新列表", UiTypography.CAPTION, UiColors.TEXT_SECONDARY));
        panel.add(Box.createHorizontalGlue());
        JButton edit = secondary("编辑所选");
        edit.addActionListener(event -> editSelected());
        panel.add(edit);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton create = primary("新建课程");
        create.addActionListener(event -> openEditor(null));
        panel.add(create);
        return panel;
    }

    private void search() {
        showState(ViewState.LOADING, "正在查询课程目录，请稍候");
        CourseCatalogQuery query = new CourseCatalogQuery(keyword.getText().trim(), activeOnly.isSelected() ? Boolean.TRUE : null, 0, 50);
        gateway.searchCatalog(query).whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            courses.clear();
            if (error != null) { showState(ViewState.DISCONNECTED, "无法读取课程目录，请检查连接后重试"); return; }
            courses.addAll(page.items());
            for (CourseView row : page.items()) model.addRow(new Object[]{
                    row.courseCode(), row.courseName(), row.credit().stripTrailingZeros().toPlainString(), row.totalHours(),
                    row.active() ? "启用" : "已停用", "v" + row.rowVersion()});
            showState(page.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    page.items().isEmpty() ? "未找到符合条件的课程，请调整查询条件" : "");
        }));
    }

    private void editSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) { showState(ViewState.ERROR, "请先选择要编辑的课程"); return; }
        openEditor(courses.get(table.convertRowIndexToModel(selected)));
    }

    private void openEditor(CourseView course) {
        CourseEditorDialog dialog = new CourseEditorDialog(SwingUtilities.getWindowAncestor(this), gateway, course, this::search);
        dialog.setVisible(true);
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }
}
