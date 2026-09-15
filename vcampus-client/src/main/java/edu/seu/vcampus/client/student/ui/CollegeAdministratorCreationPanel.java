package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.editor.EmbeddedEditor;
import edu.seu.vcampus.client.core.ui.editor.EditorSize;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.DepartmentView;
import edu.seu.vcampus.common.student.governance.CreateCollegeAdministratorCommand;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Page-embedded form for creating a college administrator account. */
public final class CollegeAdministratorCreationPanel implements EmbeddedEditor {
    private final JPanel root = new JPanel(new BorderLayout(UiSpacing.SPACE_3, UiSpacing.SPACE_3));
    private final StudentClientService students;
    private final BiConsumer<ResponseBody<?>, String> completion;
    private final Runnable close;
    private final JTextField login = new JTextField(16);
    private final JPasswordField password = new JPasswordField(16);
    private final JComboBox<Object> department = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final JButton submit = new JButton("确定创建");

    /** Creates the editor from the departments currently visible on the management page. */
    public CollegeAdministratorCreationPanel(StudentClientService students,
            ComboBoxModel<DepartmentView> departments,
            BiConsumer<ResponseBody<?>, String> completion, Runnable close) {
        this.students = Objects.requireNonNull(students);
        this.completion = Objects.requireNonNull(completion);
        this.close = Objects.requireNonNull(close);
        configureFields(departments);
        build();
    }

    private void configureFields(ComboBoxModel<DepartmentView> source) {
        login.setName("newAdminLoginField");
        password.setName("newAdminPasswordField");
        password.setText("Pass1234");
        department.setName("newAdminDeptCombo");
        department.addItem("暂不分配（未分配）");
        for (int index = 0; index < source.getSize(); index++) {
            department.addItem(source.getElementAt(index));
        }
        submit.setName("confirmCreateAdminButton");
    }

    private void build() {
        root.setName("collegeAdminCreationEditor");
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(new EmptyBorder(UiSpacing.SPACE_3, UiSpacing.SPACE_3,
                UiSpacing.SPACE_3, UiSpacing.SPACE_3));
        JPanel form = new JPanel(new GridLayout(3, 2, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        form.setOpaque(false);
        form.add(new JLabel("管理员账号：")); form.add(login);
        form.add(new JLabel("初始密码：")); form.add(password);
        form.add(new JLabel("归属学院：")); form.add(department);
        root.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        status.setForeground(UiColors.ERROR_FG);
        JButton cancel = new JButton("取消");
        cancel.addActionListener(event -> close.run());
        submit.addActionListener(event -> create());
        actions.add(status); actions.add(cancel); actions.add(submit);
        root.add(actions, BorderLayout.SOUTH);
    }

    private void create() {
        String loginId = login.getText().trim();
        String initialPassword = new String(password.getPassword()).trim();
        if (loginId.isBlank() || initialPassword.length() < 6) {
            status.setText(loginId.isBlank() ? "账号不能为空" : "密码长度不能少于 6 位");
            return;
        }
        String departmentId = department.getSelectedItem() instanceof DepartmentView value
                ? value.departmentId() : null;
        submit.setEnabled(false);
        status.setText("正在创建管理员…");
        students.createCollegeAdministrator(new CreateCollegeAdministratorCommand(
                        loginId, initialPassword, departmentId))
                .whenComplete((response, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null || response == null || !response.success()) {
                        status.setText(message(response, "创建失败，请稍后重试"));
                        submit.setEnabled(true);
                        return;
                    }
                    completion.accept(response, "管理员 " + loginId + " 创建成功");
                    close.run();
                }));
    }

    private static String message(ResponseBody<?> response, String fallback) {
        return response == null || response.message() == null || response.message().isBlank()
                ? fallback : response.message();
    }

    @Override public JComponent component() { return root; }
    @Override public EditorSize size() { return EditorSize.COMPACT; }
    @Override public boolean isDirty() {
        return !login.getText().isBlank() || department.getSelectedIndex() > 0;
    }
}
