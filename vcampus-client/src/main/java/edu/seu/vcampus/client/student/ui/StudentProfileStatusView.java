package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.StudentProfileApplicationView;

import javax.swing.Box;
import javax.swing.JLabel;
import java.awt.Component;

/** Owns the transient loading and application-status labels for the student profile page. */
final class StudentProfileStatusView {
    private final Component loadingGap = Box.createVerticalStrut(UiSpacing.SPACE_1);
    private final JLabel loading = label("student.profile.status");
    private final JLabel application = label("student.profile.application.status");

    StudentProfileStatusView() {
        showLoading("");
        showApplication(null);
    }

    Component loadingGap() { return loadingGap; }
    JLabel loadingLabel() { return loading; }
    JLabel applicationLabel() { return application; }

    void loading() { showLoading("正在加载"); }
    void failure() { showLoading("加载失败"); }
    void loaded() { showLoading(""); }

    void showApplication(StudentProfileApplicationView value) {
        if (value == null) {
            application.setText("");
            application.setVisible(false);
            return;
        }
        application.setText(switch (value.status()) {
            case DRAFT -> "已暂存，尚未提交审核";
            case PENDING -> "审核中：资料已锁定，管理员处理后方可再次编辑";
            case APPROVED -> "最近申请已通过";
            case REJECTED -> "已驳回：" + filled(value.reviewComment());
        });
        application.setVisible(true);
    }

    private void showLoading(String value) {
        boolean visible = value != null && !value.isBlank();
        loading.setText(visible ? value : "");
        loading.setVisible(visible);
        loadingGap.setVisible(visible);
    }

    private static JLabel label(String name) {
        JLabel result = new JLabel("");
        result.setName(name);
        result.setFont(UiTypography.CAPTION);
        result.setForeground(UiColors.TEXT_SECONDARY);
        return result;
    }

    private static String filled(Object value) {
        return value == null || value.toString().isBlank() ? "未填写" : value.toString();
    }
}
