package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.student.StudentChangeView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Modal dialog showing detailed change record information. */
public final class ChangeDetailDialog extends JDialog {

    public ChangeDetailDialog(Window owner, StudentChangeView change) {
        super(owner, "变更详情", ModalityType.APPLICATION_MODAL);
        Objects.requireNonNull(change, "change");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent(change));
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setSize(new Dimension(500, 400));
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent(StudentChangeView change) {
        JPanel content = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        content.setBackground(UiColors.BACKGROUND_PAGE);
        content.setBorder(UiBorders.pageInset());

        JLabel title = new JLabel("变更详情");
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        content.add(title, BorderLayout.NORTH);

        List<String[]> rows = buildRows(change);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(UiSpacing.SPACE_2, 0, UiSpacing.SPACE_2, UiSpacing.SPACE_3);
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, c, "变更类型", changeTypeLabel(change.changeType()), row++);
        for (String[] r : rows) {
            addRow(form, c, r[0], r[1], row++);
        }
        addRow(form, c, "变更原因", filled(change.reason()), row++);
        addRow(form, c, "生效日期", change.effectiveDate() == null ? "未设置" : change.effectiveDate().toString(), row++);
        addRow(form, c, "创建时间", change.createdAt() == null ? "未设置" : change.createdAt().toString(), row++);

        JScrollPane scroll = new JScrollPane(form,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.setBorder(new javax.swing.border.EmptyBorder(0, 0, 0, 0));
        scroll.getViewport().setOpaque(false);
        content.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.setOpaque(false);
        JButton close = new JButton("关闭");
        close.setFont(UiTypography.BODY);
        close.addActionListener(e -> dispose());
        bottom.add(close);
        content.add(bottom, BorderLayout.SOUTH);

        return content;
    }

    private static List<String[]> buildRows(StudentChangeView change) {
        List<String[]> rows = new ArrayList<>();
        String type = change.changeType();
        String oldVal = change.oldValue();
        String newVal = change.newValue();

        if ("STATUS_CHANGE".equals(type)) {
            rows.add(new String[]{"学籍状态", translateStatus(oldVal) + " → " + translateStatus(newVal)});
        } else if ("CLASS_CHANGE".equals(type) || "ENROLLMENT_CHANGE".equals(type)) {
            String[] oldParts = parseEnrollment(oldVal);
            String[] newParts = parseEnrollment(newVal);
            if (!oldParts[0].equals(newParts[0])) {
                rows.add(new String[]{"学号", oldParts[0] + " → " + newParts[0]});
            }
            if (!oldParts[1].equals(newParts[1])) {
                rows.add(new String[]{"班级", oldParts[1] + " → " + newParts[1]});
            }
            if (rows.isEmpty()) {
                rows.add(new String[]{"变更内容", oldVal + " → " + newVal});
            }
        } else if ("PROFILE_CHANGE".equals(type)) {
            rows.add(new String[]{"变更前", filled(oldVal)});
            rows.add(new String[]{"变更后", filled(newVal)});
        } else if ("ADMISSION".equals(type)) {
            rows.add(new String[]{"录取信息", filled(oldVal)});
        } else {
            if (oldVal != null && !oldVal.isBlank()) rows.add(new String[]{"变更前", oldVal});
            if (newVal != null && !newVal.isBlank()) rows.add(new String[]{"变更后", newVal});
        }
        return rows;
    }

    private static String[] parseEnrollment(String value) {
        // Format: classId:studentNumber
        if (value == null || value.isBlank()) return new String[]{"", ""};
        int sep = value.indexOf(':');
        if (sep > 0 && sep < value.length() - 1) {
            String studentNumber = value.substring(sep + 1);
            String classDisplay = studentNumber.length() >= 6
                    ? studentNumber.substring(0, 6) + "班" : studentNumber;
            return new String[]{studentNumber, classDisplay};
        }
        return new String[]{value, value};
    }

    private void addRow(JPanel form, GridBagConstraints c, String label, String value, int row) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label);
        l.setFont(UiTypography.CAPTION);
        l.setForeground(UiColors.TEXT_SECONDARY);
        form.add(l, c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JTextArea ta = new JTextArea(value);
        ta.setFont(UiTypography.BODY);
        ta.setForeground(UiColors.TEXT_PRIMARY);
        ta.setOpaque(false);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setRows(Math.max(1, value.split("\n").length));
        form.add(ta, c);
    }

    private static String changeTypeLabel(String type) {
        if (type == null) return "未知";
        return switch (type) {
            case "ADMISSION" -> "录取";
            case "CLASS_CHANGE" -> "转班";
            case "STATUS_CHANGE" -> "状态变更";
            case "ENROLLMENT_CHANGE" -> "学籍变更";
            case "PROFILE_CHANGE" -> "信息修改";
            default -> type;
        };
    }

    private static String translateStatus(String status) {
        if (status == null) return "未知";
        return switch (status) {
            case "ACTIVE" -> "正常";
            case "SUSPENDED" -> "休学";
            case "GRADUATED" -> "已毕业";
            case "WITHDRAWN" -> "已退学";
            default -> status;
        };
    }

    private static String filled(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }
}
