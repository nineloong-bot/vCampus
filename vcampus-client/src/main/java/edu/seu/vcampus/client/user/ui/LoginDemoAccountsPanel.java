package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridLayout;

/** Accessible demo accounts reference panel displayed on the login screen. */
final class LoginDemoAccountsPanel extends JPanel {
    LoginDemoAccountsPanel() {
        super(new GridLayout(0, 1, 0, UiSpacing.SPACE_1));
        setOpaque(false);
        getAccessibleContext().setAccessibleName("课程演示账号");
        add(demoLabel("演示账号", "login.demoTitle"));
        add(demoLabel("管理员：admin / 123456", "login.demoAdmin"));
        add(demoLabel("身份：SUPER_ADMIN（超管 admin）", "login.demoAdminRole"));
        add(demoLabel("教师：teacher / 123456（T001-T024）", "login.demoTeacher"));
        add(demoLabel("学生：213240001 等一卡通或学号 / 123456", "login.demoStudent"));
        add(demoLabel("演示统一密码：123456", "login.demoManagementPassword"));
        add(demoLabel("模块：学籍 student (stu) ｜ 课程 course", "login.demoModuleAdmins1"));
        add(demoLabel("模块：图书 library (lib) ｜ 商城 shop", "login.demoModuleAdmins2"));
        add(demoLabel("模块：用户 user", "login.demoModuleAdmins3"));
        add(demoLabel("学院：计算机 csadmin (cs) ｜ 数学 mathadmin (math)", "login.demoCollegeAdmins"));
    }

    private static JLabel demoLabel(String text, String name) {
        JLabel label = new JLabel(text);
        label.setName(name);
        label.getAccessibleContext().setAccessibleName(text);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        return label;
    }
}
