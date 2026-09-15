package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;

import javax.swing.JComboBox;
import java.util.ArrayList;
import java.util.List;

/** Presentation labels and typed filter helpers for account management. */
final class UserManagementLabels {
    private UserManagementLabels() { }

    static String role(UserRole value) {
        return switch (value) {
            case SUPER_ADMIN -> "超级管理员";
            case STUDENT_ADMIN -> "学籍管理员";
            case COLLEGE_ADMIN -> "学院管理员";
            case COURSE_ADMIN -> "课程管理员";
            case LIBRARY_ADMIN -> "图书管理员";
            case SHOP_ADMIN -> "商城管理员";
            case USER_ADMIN -> "用户管理员";
            case STUDENT -> "学生";
            case TEACHER -> "教师";
            case ADMIN -> "管理员";
        };
    }

    static String status(AccountStatus value) {
        return switch (value) {
            case ACTIVE -> "正常";
            case PENDING -> "待审核";
            case DISABLED -> "已停用";
            case CANCELLED -> "已注销";
        };
    }

    static Object[] filters(Object[] values) {
        List<Object> result = new ArrayList<>();
        result.add("全部");
        result.addAll(List.of(values));
        return result.toArray();
    }

    static <T> T selected(JComboBox<Object> box, Class<T> type) {
        return type.isInstance(box.getSelectedItem()) ? type.cast(box.getSelectedItem()) : null;
    }
}
