package edu.seu.vcampus.client.user.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.user.AccountStatus;
import edu.seu.vcampus.common.user.UserRole;
import edu.seu.vcampus.common.user.UserSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detail panel showing the selected user's profile and account status.
 * Follows the master-detail visual pattern established in student management.
 */
public final class UserDetailPanel extends JPanel {
    private static final Color TABLE_BORDER = new Color(178, 218, 211);
    private static final Color TABLE_LABEL = new Color(239, 247, 245);
    private static final Color SECTION_ACCENT = new Color(52, 151, 136);

    private final CardLayout cards = new CardLayout();
    private final Map<String, JLabel> fields = new LinkedHashMap<>();

    /** Creates an empty detail panel displaying placeholder. */
    public UserDetailPanel() {
        super();
        setLayout(cards);
        setOpaque(false);
        setBorder(UiBorders.pageInset());

        add(placeholder(), "placeholder");
        add(detailView(), "detail");
        cards.show(this, "placeholder");
    }

    private JPanel placeholder() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel label = new JLabel("点击账户查看详情", SwingConstants.CENTER);
        label.setFont(UiTypography.SECTION_TITLE);
        label.setForeground(UiColors.TEXT_SECONDARY);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private JPanel detailView() {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, UiSpacing.SPACE_2));
        header.setOpaque(false);
        header.setAlignmentX(LEFT_ALIGNMENT);
        JLabel accent = new JLabel(" ");
        accent.setOpaque(true);
        accent.setBackground(SECTION_ACCENT);
        accent.setPreferredSize(new Dimension(6, 26));
        header.add(accent);
        JLabel title = new JLabel("账户详细信息");
        title.setFont(UiTypography.SECTION_TITLE.deriveFont(Font.BOLD, 18f));
        title.setForeground(UiColors.TEXT_PRIMARY);
        header.add(title);
        content.add(header);
        content.add(Box.createVerticalStrut(UiSpacing.SPACE_3));

        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        table.setAlignmentX(LEFT_ALIGNMENT);

        String[][] definitions = {
                {"loginId", "登录标识"},
                {"role", "用户角色"},
                {"status", "账户状态"},
                {"lastLogin", "最近登录"},
                {"version", "数据版本"}
        };

        for (int i = 0; i < definitions.length; i++) {
            String key = definitions[i][0];
            String labelText = definitions[i][1];
            JLabel label = cell(labelText, true);
            JLabel value = cell("—", false);
            fields.put(key, value);
            addCell(table, label, 0, i, 0.3);
            addCell(table, value, 1, i, 0.7);
        }
        content.add(table);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.add(content, BorderLayout.NORTH);
        return outer;
    }

    /** Updates the panel with the selected user's summary data. */
    public void showUser(UserSummary user) {
        if (user == null) {
            clear();
            return;
        }
        setField("loginId", user.loginId());
        setField("role", roleName(user.role()));
        setField("status", statusName(user.accountStatus()));
        setField("lastLogin", user.lastLoginAt() != null ? user.lastLoginAt().toString() : "尚未登录");
        setField("version", String.valueOf(user.rowVersion()));
        cards.show(this, "detail");
    }

    /** Clears the details and displays the empty placeholder. */
    public void clear() {
        cards.show(this, "placeholder");
    }

    private void setField(String key, String value) {
        JLabel label = fields.get(key);
        if (label != null) {
            label.setText(value != null ? value : "—");
            label.setToolTipText(value);
        }
    }

    private static void addCell(JPanel table, JLabel cell, int x, int y, double weight) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weight;
        c.fill = GridBagConstraints.BOTH;
        table.add(cell, c);
    }

    private static JLabel cell(String text, boolean isLabel) {
        JLabel result = new JLabel(text);
        result.setFont(isLabel ? UiTypography.BODY.deriveFont(Font.BOLD) : UiTypography.BODY);
        result.setForeground(UiColors.TEXT_PRIMARY);
        result.setOpaque(true);
        result.setBackground(isLabel ? TABLE_LABEL : Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(TABLE_BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        result.setMinimumSize(new Dimension(100, 36));
        return result;
    }

    private static String roleName(UserRole value) {
        if (value == null) return "—";
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

    private static String statusName(AccountStatus value) {
        if (value == null) return "—";
        return switch (value) {
            case ACTIVE -> "正常";
            case PENDING -> "待审核";
            case DISABLED -> "已停用";
            case CANCELLED -> "已注销";
        };
    }
}
