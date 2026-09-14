package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.CreateCollegeAdministratorCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.BiConsumer;

/** Builds the account-creation dialog used by college-administrator governance. */
final class CollegeAdministratorCreationDialog {
    private CollegeAdministratorCreationDialog() {
    }

    static void show(Component owner, StudentClientService students,
            ComboBoxModel<DepartmentView> departments, JLabel status,
            BiConsumer<ResponseBody<?>, String> completion) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
                "新增学院管理员", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        dialog.getRootPane().setBorder(new EmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3,
                UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        JTextField login = new JTextField(16);
        login.setName("newAdminLoginField");
        JPasswordField password = new JPasswordField(16);
        password.setName("newAdminPasswordField");
        password.setText("Pass1234");
        JComboBox<Object> department = departments(departments);
        dialog.add(form(login, password, department), BorderLayout.CENTER);
        dialog.add(actions(dialog, students, login, password, department, status, completion),
                BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private static JComboBox<Object> departments(ComboBoxModel<DepartmentView> source) {
        JComboBox<Object> result = new JComboBox<>();
        result.setName("newAdminDeptCombo");
        result.addItem("暂不分配（未分配）");
        for (int index = 0; index < source.getSize(); index++) result.addItem(source.getElementAt(index));
        return result;
    }

    private static JPanel form(JTextField login, JPasswordField password,
            JComboBox<Object> department) {
        JPanel form = new JPanel(new GridLayout(3, 2, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        form.add(new JLabel("管理员账号："));
        form.add(login);
        form.add(new JLabel("初始密码："));
        form.add(password);
        form.add(new JLabel("归属学院："));
        form.add(department);
        return form;
    }

    private static JPanel actions(JDialog dialog, StudentClientService students,
            JTextField login, JPasswordField password, JComboBox<Object> department,
            JLabel status, BiConsumer<ResponseBody<?>, String> completion) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("取消");
        JButton confirm = new JButton("确定创建");
        confirm.setName("confirmCreateAdminButton");
        cancel.addActionListener(event -> dialog.dispose());
        confirm.addActionListener(event -> create(dialog, students, login, password,
                department, status, completion));
        actions.add(cancel);
        actions.add(confirm);
        return actions;
    }

    private static void create(JDialog dialog, StudentClientService students,
            JTextField loginField, JPasswordField passwordField, JComboBox<Object> department,
            JLabel status, BiConsumer<ResponseBody<?>, String> completion) {
        String login = loginField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        if (login.isBlank() || password.length() < 6) {
            String message = login.isBlank() ? "账号不能为空" : "密码长度不能少于 6 位";
            JOptionPane.showMessageDialog(dialog, message, "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String departmentId = department.getSelectedItem() instanceof DepartmentView value
                ? value.departmentId() : null;
        dialog.dispose();
        status.setText("正在创建管理员...");
        students.createCollegeAdministrator(new CreateCollegeAdministratorCommand(
                        login, password, departmentId))
                .whenComplete((response, failure) -> completion.accept(response,
                        "管理员 " + login + " 创建成功"));
    }
}
