package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CourseCatalogQuery;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteChoice;
import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteSelectionField;

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
    private final AutocompleteSelectionField department;
    private final JCheckBox activeOnly = new JCheckBox("仅显示启用课程");
    private final DefaultTableModel model = readOnlyModel("课程代码", "课程名称", "学分", "总学时", "状态", "开课学院");
    private final JTable table = table(new Object[0][0], new Object[0]);
    private final CoursePager pager;

    public CourseCatalogPanel(CourseUiGateway gateway) {
        super("课程目录");
        this.gateway = gateway;
        this.department = new AutocompleteSelectionField((query, limit) -> gateway.listCourseDepartments()
                .thenApply(options -> options.stream()
                        .filter(option -> option.departmentName().contains(query))
                        .limit(limit)
                        .map(option -> new AutocompleteChoice(option.departmentId(),
                                option.departmentName(), ""))
                        .toList()));
        this.department.onSelection(ignored -> search(0));
        this.pager = new CoursePager(50, this::search);
        table.setModel(model);
        table.getTableHeader().setBackground(UiColors.BACKGROUND_SUBTLE);
        table.getAccessibleContext().setAccessibleName("课程目录列表");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        JPanel listing = new JPanel(new BorderLayout(0, UiSpacing.MD));
        listing.setOpaque(false);
        listing.add(filters(), BorderLayout.NORTH);
        JPanel tableArea = new JPanel(new BorderLayout(0, UiSpacing.MD));
        tableArea.setOpaque(false);
        tableArea.add(scroll, BorderLayout.CENTER);
        tableArea.add(pager, BorderLayout.SOUTH);
        listing.add(tableArea, BorderLayout.CENTER);
        body.add(listing, BorderLayout.CENTER);
        search(0);
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
        panel.add(label("开课学院", UiTypography.BODY, UiColors.TEXT_PRIMARY));
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        department.setPreferredSize(new Dimension(220, UiDimensions.CONTROL_HEIGHT));
        department.setMaximumSize(new Dimension(260, UiDimensions.CONTROL_HEIGHT));
        department.inputComponent().setFont(UiTypography.BODY);
        department.inputComponent().getAccessibleContext().setAccessibleName("开课学院");
        department.inputComponent().setToolTipText("输入学院名称并从匹配结果中选择");
        panel.add(department);
        panel.add(Box.createHorizontalStrut(UiSpacing.LG));
        activeOnly.setOpaque(false);
        activeOnly.setFont(UiTypography.BODY);
        panel.add(activeOnly);
        panel.add(Box.createHorizontalGlue());
        JButton reset = secondary("重置条件");
        reset.addActionListener(event -> { keyword.setText(""); department.inputComponent().setText(""); activeOnly.setSelected(false); search(0); });
        panel.add(reset);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        JButton search = secondary("查询课程");
        search.addActionListener(event -> search(0));
        panel.add(search);
        return panel;
    }

    private void search(int pageNumber) {
        if (!department.inputComponent().getText().isBlank() && department.selectedId().isEmpty()) {
            showState(ViewState.EMPTY, "请从匹配结果中选择开课学院");
            return;
        }
        long request = beginAsyncRequest();
        showState(ViewState.LOADING, "正在查询课程目录，请稍候");
        String departmentName = department.selectedId().isPresent()
                ? department.inputComponent().getText().trim() : null;
        CourseCatalogQuery query = new CourseCatalogQuery(keyword.getText().trim(),
                activeOnly.isSelected() ? Boolean.TRUE : null,
                department.selectedId().orElse(null), departmentName, pageNumber, 50);
        gateway.searchCatalog(query).whenComplete((page, error) -> SwingUtilities.invokeLater(() -> {
            if (!acceptsAsyncResult(request)) return;
            if (error != null) { showState(ViewState.DISCONNECTED, "无法读取课程目录，请检查连接后重试"); return; }
            model.setRowCount(0);
            for (CourseView row : page.items()) model.addRow(new Object[]{
                    row.courseCode(), row.courseName(), row.credit().stripTrailingZeros().toPlainString(), row.totalHours(),
                    row.active() ? "启用" : "已停用", college(row)});
            pager.showPage(page.page(), page.total());
            showState(page.items().isEmpty() ? ViewState.EMPTY : ViewState.NORMAL,
                    page.items().isEmpty() ? "未找到符合条件的课程，请调整查询条件" : "");
        }));
    }

    @Override protected void refreshAfterNavigation() { search(pager.currentPage()); }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) { public boolean isCellEditable(int row, int column) { return false; } };
    }

    private static String college(CourseView course) {
        return course.departmentName() == null || course.departmentName().isBlank()
                ? "未设置" : course.departmentName();
    }
}
