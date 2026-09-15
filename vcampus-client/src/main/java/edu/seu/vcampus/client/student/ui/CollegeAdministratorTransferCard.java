package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.StudentCollegeAdministratorView;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Transfer form card for transferring an assigned college administrator to a new department.
 */
public final class CollegeAdministratorTransferCard extends JPanel {
    private final BiConsumer<StudentCollegeAdministratorView, DepartmentView> onTransfer;
    private final Consumer<StudentCollegeAdministratorView> onDeactivate;

    private final JLabel loginLabel = new JLabel("-");
    private final JLabel currentDeptLabel = new JLabel("-");
    private final JComboBox<DepartmentView> targetDeptCombo = new JComboBox<>();
    private final JButton transferBtn = new JButton("确认调动");
    private final JLabel promptLabel = new JLabel(" ");
    private StudentCollegeAdministratorView currentAdmin;

    /**
     * Creates the transfer card.
     *
     * @param onTransfer callback when user confirms transfer
     * @param onDeactivate callback when user deactivates the administrator
     */
    public CollegeAdministratorTransferCard(
            BiConsumer<StudentCollegeAdministratorView, DepartmentView> onTransfer,
            Consumer<StudentCollegeAdministratorView> onDeactivate) {
        super(new BorderLayout(0, UiSpacing.SPACE_3));
        this.onTransfer = Objects.requireNonNull(onTransfer, "onTransfer");
        this.onDeactivate = Objects.requireNonNull(onDeactivate, "onDeactivate");
        setOpaque(false);
        build();
    }

    private void build() {
        JLabel title = new JLabel("管理员学院调动");
        title.setFont(UiTypography.SECTION_TITLE);
        add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, UiSpacing.SPACE_2));
        center.setOpaque(false);

        JPanel form = new JPanel(new GridLayout(3, 2, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        form.setOpaque(false);
        form.add(new JLabel("管理员账号："));
        form.add(loginLabel);
        form.add(new JLabel("当前所属学院："));
        form.add(currentDeptLabel);
        form.add(new JLabel("调动目标学院："));
        targetDeptCombo.setName("collegeAdminTransferDeptCombo");
        form.add(targetDeptCombo);

        center.add(form, BorderLayout.CENTER);
        promptLabel.setFont(UiTypography.CAPTION);
        promptLabel.setForeground(UiColors.ERROR_FG);
        center.add(promptLabel, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        transferBtn.setName("collegeAdminTransferButton");
        transferBtn.addActionListener(e -> {
            DepartmentView target = (DepartmentView) targetDeptCombo.getSelectedItem();
            if (currentAdmin != null && target != null) onTransfer.accept(currentAdmin, target);
        });

        JButton deactivateBtn = new JButton("停用管理员");
        deactivateBtn.setName("collegeAdminDeactivateButton");
        deactivateBtn.addActionListener(e -> {
            if (currentAdmin != null) onDeactivate.accept(currentAdmin);
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.add(deactivateBtn);
        actions.add(transferBtn);
        add(actions, BorderLayout.SOUTH);
    }

    /**
     * Displays the transfer form for the given administrator.
     *
     * @param admin the assigned administrator
     * @param departments the active departments list
     */
    public void display(StudentCollegeAdministratorView admin, List<DepartmentView> departments) {
        this.currentAdmin = admin;
        loginLabel.setText(admin == null ? "-" : admin.loginId());
        currentDeptLabel.setText(admin == null || admin.departmentName() == null
                ? "未分配" : admin.departmentName());
        targetDeptCombo.removeAllItems();
        if (departments != null && admin != null) {
            departments.stream()
                    .filter(DepartmentView::active)
                    .filter(d -> !Objects.equals(d.departmentId(), admin.departmentId()))
                    .forEach(targetDeptCombo::addItem);
        }
        boolean hasAlternative = targetDeptCombo.getItemCount() > 0;
        transferBtn.setEnabled(hasAlternative);
        targetDeptCombo.setEnabled(hasAlternative);
        promptLabel.setText(hasAlternative ? " " : "当前无其他可选学院，无法调动");
    }

    /** Requests focus on the target department dropdown. */
    public void focusChoice() {
        targetDeptCombo.requestFocusInWindow();
    }
}
