package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
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
    private final DefaultComboBoxModel<DepartmentView> departmentsModel = new DefaultComboBoxModel<>();
    private final JLabel status = new JLabel(" ");
    private final CollegeAdministratorWorkspacePanel workspace;
    private List<StudentCollegeAdministratorView> administrators = List.of();

    /** Creates the college-administrator governance workspace. */
    public CollegeAdministratorManagementPanel(StudentClientService students) {
        super(new BorderLayout(UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        this.students = Objects.requireNonNull(students);
        this.workspace = new CollegeAdministratorWorkspacePanel(
                this::assign, this::transfer, this::deactivate);
        setName("college-administrator.management");
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(new EmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3,
                UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        build();
    }

    private void build() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.setOpaque(false);
        toolbar.add(button("新增管理员", "collegeAdminCreateButton", e -> showCreateDialog()));
        toolbar.add(button("分配", "collegeAdminToolbarAssignButton", e -> toolbarAssign()));
        toolbar.add(button("调动", "collegeAdminToolbarTransferButton", e -> toolbarTransfer()));
        toolbar.add(button("刷新", "collegeAdminRefreshButton", e -> refresh()));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            StudentCollegeAdministratorView admin = selected();
            workspace.showAdministrator(admin);
            if (admin != null) {
                status.setText(admin.assigned()
                        ? "选中管理员 " + admin.loginId() + "（" + admin.departmentName() + "），可在工作区调动或停用"
                        : "选中未分配管理员 " + admin.loginId() + "，请在右侧工作区选择学院分配");
            }
        });

        JPanel leftPanel = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        leftPanel.setOpaque(false);
        leftPanel.add(toolbar, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, workspace);
        split.setResizeWeight(0.58);
        split.setDividerSize(6);
        split.setOneTouchExpandable(true);
        split.setBorder(null);
        split.setOpaque(false);

        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
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
                    departmentsModel.removeAllElements();
                    var active = response.data().departments().stream()
                            .filter(DepartmentView::active).toList();
                    active.forEach(departmentsModel::addElement);
                    workspace.setDepartments(active);
                    workspace.showAdministrator(selected());
                    status.setText("已加载 " + administrators.size() + " 名学院管理员");
                }));
    }

    private void toolbarAssign() {
        StudentCollegeAdministratorView admin = selected();
        if (admin == null || admin.assigned()) {
            status.setText(admin == null ? "请选择未分配管理员" : "该管理员已分配学院，如需更换请在工作区调动");
            return;
        }
        workspace.showAssign();
        workspace.focusChoice();
        status.setText("请在右侧工作区选择目标学院并确认分配");
    }

    private void toolbarTransfer() {
        StudentCollegeAdministratorView admin = selected();
        if (admin == null || !admin.assigned()) {
            status.setText(admin == null ? "请选择已分配管理员" : "该管理员未分配学院，请在工作区分配");
            return;
        }
        workspace.showTransfer();
        workspace.focusChoice();
        status.setText("请在右侧工作区选择目标学院并确认调动");
    }

    private void assign(StudentCollegeAdministratorView administrator, DepartmentView department) {
        if (administrator == null || department == null || administrator.assigned()) {
            status.setText("请选择未分配管理员和目标学院");
            return;
        }
        students.assignCollegeAdministrator(new AssignStudentCollegeAdministratorCommand(
                department.departmentId(), administrator.userId(), department.rowVersion()))
                .whenComplete((response, failure) -> complete(response, "分配完成"));
    }

    private void transfer(StudentCollegeAdministratorView administrator, DepartmentView target) {
        if (administrator == null || target == null || !administrator.assigned()) {
            status.setText("请选择已分配管理员和目标学院");
            return;
        }
        if (Objects.equals(administrator.departmentId(), target.departmentId())) {
            status.setText("调动目标学院不能与当前学院相同");
            return;
        }
        students.transferCollegeAdministrator(new TransferStudentCollegeAdministratorCommand(
                administrator.userId(), administrator.departmentId(), target.departmentId(),
                administrator.assignmentVersion(), target.rowVersion()))
                .whenComplete((response, failure) -> complete(response, "调动完成"));
    }

    private void deactivate(StudentCollegeAdministratorView administrator) {
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
        CollegeAdministratorCreationDialog.show(this, students, departmentsModel,
                status, this::complete);
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
