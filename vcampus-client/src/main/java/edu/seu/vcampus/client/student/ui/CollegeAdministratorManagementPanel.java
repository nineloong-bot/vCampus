package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditorHost;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.AssignStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.DeactivateStudentCollegeAdministratorCommand;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;
import edu.seu.vcampus.common.student.governance.TransferStudentCollegeAdministratorCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lets the student administrator assign, transfer and deactivate college administrators. */
public final class CollegeAdministratorManagementPanel extends JPanel {
    private final StudentClientService students;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"账号", "状态", "学院", "绑定"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JComboBox<DepartmentView> departments = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private EmbeddedEditorHost editorHost;
    private List<StudentCollegeAdministratorView> administrators = List.of();

    /** Creates the college-administrator governance workspace. */
    public CollegeAdministratorManagementPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        setName("college-administrator.management");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new EmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3,
                UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        build();
    }

    private void build() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setOpaque(false);
        JButton create = button("新增管理员", "collegeAdminCreateButton", event -> showCreateDialog());
        JButton refresh = button("刷新", "collegeAdminRefreshButton", event -> refresh());
        JButton assign = button("分配", "collegeAdminAssignButton", event -> assign());
        JButton transfer = button("调动", "collegeAdminTransferButton", event -> transfer());
        JButton deactivate = button("停用", "collegeAdminDeactivateButton",
                event -> deactivate());
        toolbar.add(create);
        toolbar.add(new JLabel("目标学院："));
        toolbar.add(departments);
        toolbar.add(assign);
        toolbar.add(transfer);
        toolbar.add(deactivate);
        toolbar.add(refresh);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel list = new JPanel(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        list.setOpaque(false);
        list.add(toolbar, BorderLayout.NORTH);
        list.add(new JScrollPane(table), BorderLayout.CENTER);
        list.add(status, BorderLayout.SOUTH);
        editorHost = new EmbeddedEditorHost(list);
        editorHost.setOpaque(false);
        add(editorHost, BorderLayout.CENTER);
    }

    @Override public void addNotify() {
        super.addNotify();
        refresh();
    }

    private void refresh() {
        students.searchCollegeAdministrators().whenComplete((response, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (response == null || !response.success()) {
                        status.setText(message(response, "加载失败"));
                        return;
                    }
                    administrators = response.data().administrators();
                    model.setRowCount(0);
                    administrators.forEach(row -> model.addRow(new Object[] {
                            row.loginId(), row.accountStatus(),
                            row.departmentName() == null ? "未分配" : row.departmentName(),
                            row.assigned() ? "有效" : "未绑定"}));
                    departments.removeAllItems();
                    response.data().departments().stream().filter(DepartmentView::active)
                            .forEach(departments::addItem);
                    status.setText(" ");
                }));
    }

    private void assign() {
        StudentCollegeAdministratorView administrator = selected();
        DepartmentView department = (DepartmentView) departments.getSelectedItem();
        if (administrator == null || department == null || administrator.assigned()) {
            status.setText("请选择未分配管理员和目标学院");
            return;
        }
        students.assignCollegeAdministrator(new AssignStudentCollegeAdministratorCommand(
                department.departmentId(), administrator.userId(), department.rowVersion()))
                .whenComplete((response, failure) -> complete(response, "分配完成"));
    }

    private void transfer() {
        StudentCollegeAdministratorView administrator = selected();
        DepartmentView target = (DepartmentView) departments.getSelectedItem();
        if (administrator == null || target == null || !administrator.assigned()) {
            status.setText("请选择已分配管理员和目标学院");
            return;
        }
        students.transferCollegeAdministrator(new TransferStudentCollegeAdministratorCommand(
                administrator.userId(), administrator.departmentId(), target.departmentId(),
                administrator.assignmentVersion(), target.rowVersion()))
                .whenComplete((response, failure) -> complete(response, "调动完成"));
    }

    private void deactivate() {
        StudentCollegeAdministratorView administrator = selected();
        if (administrator == null || !administrator.assigned()) {
            status.setText("请选择已分配管理员");
            return;
        }
        students.deactivateCollegeAdministrator(new DeactivateStudentCollegeAdministratorCommand(
                administrator.departmentId(), administrator.userId(),
                administrator.assignmentVersion()))
                .whenComplete((response, failure) -> complete(response, "停用完成"));
    }

    private void showCreateDialog() {
        CollegeAdministratorCreationPanel editor = new CollegeAdministratorCreationPanel(
                students, departments.getModel(), this::complete,
                () -> editorHost.completeAndClose());
        editorHost.showEditor(editor);
    }

    private StudentCollegeAdministratorView selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= administrators.size() ? null : administrators.get(row);
    }

    private void complete(edu.seu.vcampus.common.protocol.ResponseBody<?> response,
            String success) {
        SwingUtilities.invokeLater(() -> {
            status.setText(response != null && response.success()
                    ? success : message(response, "操作失败"));
            if (response != null && response.success()) refresh();
        });
    }

    private static JButton button(String text, String name,
            java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setName(name);
        button.addActionListener(listener);
        return button;
    }

    private static String message(edu.seu.vcampus.common.protocol.ResponseBody<?> response,
            String fallback) {
        return response == null || response.message() == null ? fallback : response.message();
    }
}
