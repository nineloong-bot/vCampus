package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Assignment form card for assigning an unassigned college administrator to a department.
 */
public final class CollegeAdministratorAssignCard extends JPanel {
    private final BiConsumer<StudentCollegeAdministratorView, DepartmentView> onAssign;
    private final JLabel loginLabel = new JLabel("-");
    private final JComboBox<DepartmentView> deptCombo = new JComboBox<>();
    private StudentCollegeAdministratorView currentAdmin;

    /**
     * Creates the assignment card.
     *
     * @param onAssign callback when user confirms the assignment
     */
    public CollegeAdministratorAssignCard(
            BiConsumer<StudentCollegeAdministratorView, DepartmentView> onAssign) {
        super(new BorderLayout(0, UiSpacing.SPACE_3));
        this.onAssign = Objects.requireNonNull(onAssign, "onAssign");
        setOpaque(false);
        build();
    }

    private void build() {
        JLabel title = new JLabel("管理员分配学院");
        title.setFont(UiTypography.SECTION_TITLE);
        add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(2, 2, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        form.setOpaque(false);
        form.add(new JLabel("管理员账号："));
        form.add(loginLabel);
        form.add(new JLabel("分配至学院："));
        deptCombo.setName("collegeAdminAssignDeptCombo");
        form.add(deptCombo);
        add(form, BorderLayout.CENTER);

        JButton assignBtn = new JButton("确认分配");
        assignBtn.setName("collegeAdminAssignButton");
        assignBtn.addActionListener(e -> {
            DepartmentView dept = (DepartmentView) deptCombo.getSelectedItem();
            if (currentAdmin != null && dept != null) onAssign.accept(currentAdmin, dept);
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(assignBtn);
        add(actions, BorderLayout.SOUTH);
    }

    /**
     * Displays the assignment form for the given administrator.
     *
     * @param admin the unassigned administrator
     * @param departments the assignable departments
     */
    public void display(StudentCollegeAdministratorView admin, List<DepartmentView> departments) {
        this.currentAdmin = admin;
        loginLabel.setText(admin == null ? "-" : admin.loginId());
        deptCombo.removeAllItems();
        if (departments != null) {
            departments.stream().filter(DepartmentView::active).forEach(deptCombo::addItem);
        }
    }

    /** Requests focus on the department selection dropdown. */
    public void focusChoice() {
        deptCombo.requestFocusInWindow();
    }
}
