package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CourseView;
import edu.seu.vcampus.common.course.CreateCourseCommand;
import edu.seu.vcampus.common.course.UpdateCourseCommand;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/** Modal create/edit form for one catalog course. */
final class CourseEditorDialog extends JDialog {
    private final UiAsyncGuard asyncGuard = new UiAsyncGuard();
    private final CourseUiGateway gateway;
    private final CourseView existing;
    private final Runnable onSaved;
    private final JTextField code = field("课程代码");
    private final JTextField name = field("课程名称");
    private final JSpinner credit = creditSpinner(new BigDecimal("1.0"),
            new BigDecimal("0.5"), new BigDecimal("20.0"), new BigDecimal("0.5"), "学分");
    private final JSpinner hours = spinner(32, 1, 1000, 1, "总学时");
    private final JTextArea description = new JTextArea(5, 40);
    private final JCheckBox active = new JCheckBox("启用课程", true);
    private final JLabel error = label(" ", UiColors.ACCENT);
    private final JButton save;

    CourseEditorDialog(Window owner, CourseUiGateway gateway, CourseView existing, Runnable onSaved) {
        super(owner, existing == null ? "新建课程" : "编辑课程", ModalityType.APPLICATION_MODAL);
        this.gateway = gateway;
        this.existing = existing;
        this.onSaved = onSaved;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.LG));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        root.add(title(existing == null ? "新建课程" : "编辑课程"), BorderLayout.NORTH);
        root.add(form(), BorderLayout.CENTER);
        save = AbstractCoursePanel.primary(existing == null ? "创建课程" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(actions(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(save);
        if (existing != null) fill(existing);
        pack();
        setSize(new Dimension(560, 620));
        setLocationRelativeTo(owner);
    }

    private JPanel title(String text) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(text);
        heading.setFont(UiTypography.PAGE_TITLE);
        heading.setForeground(UiColors.TEXT_PRIMARY);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label("必填字段：课程代码、课程名称、学分、总学时", UiColors.TEXT_SECONDARY));
        return panel;
    }

    private JPanel form() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(row("课程代码（必填）", code));
        panel.add(row("课程名称（必填）", name));
        panel.add(row("学分（必填）", credit));
        panel.add(row("总学时（必填）", hours));
        JLabel descriptionLabel = label("课程简介", UiColors.TEXT_PRIMARY);
        panel.add(descriptionLabel);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        description.setFont(UiTypography.BODY);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.getAccessibleContext().setAccessibleName("课程简介");
        JScrollPane scroll = new JScrollPane(description);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(UiSpacing.MD));
        active.setFont(UiTypography.BODY);
        active.setOpaque(false);
        panel.add(active);
        return panel;
    }

    private JPanel row(String labelText, Component field) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.add(label(labelText, UiColors.TEXT_PRIMARY));
        row.add(Box.createVerticalStrut(UiSpacing.SM));
        row.add(field);
        row.add(Box.createVerticalStrut(UiSpacing.MD));
        return row;
    }

    private JPanel actions() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.BORDER_DEFAULT));
        panel.add(error);
        panel.add(Box.createHorizontalGlue());
        JButton cancel = AbstractCoursePanel.secondary("取消");
        cancel.addActionListener(event -> dispose());
        panel.add(cancel);
        panel.add(Box.createHorizontalStrut(UiSpacing.SM));
        panel.add(save);
        return panel;
    }

    private void submit() {
        error.setText(" ");
        CompletableFuture<CourseView> request;
        try {
            String cleanCode = required(code, "请输入课程代码");
            String cleanName = required(name, "请输入课程名称");
            BigDecimal cleanCredit = decimal((Number) credit.getValue());
            int cleanHours = ((Number) hours.getValue()).intValue();
            if (existing == null) {
                request = gateway.createCourse(new CreateCourseCommand(cleanCode, cleanName, cleanCredit, cleanHours,
                        description.getText().trim(), active.isSelected()));
            } else {
                request = gateway.updateCourse(new UpdateCourseCommand(existing.courseId(), cleanCode, cleanName,
                        cleanCredit, cleanHours, description.getText().trim(), active.isSelected(), existing.rowVersion()));
            }
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage() == null ? "请检查必填字段" : invalid.getMessage());
            return;
        }
        String idle = save.getText();
        save.setEnabled(false);
        save.setText(existing == null ? "正在创建…" : "正在保存…");
        long asyncRequest = asyncGuard.begin();
        request.whenComplete((saved, failure) -> SwingUtilities.invokeLater(() -> {
            if (!asyncGuard.accepts(asyncRequest)) return;
            save.setEnabled(true);
            save.setText(idle);
            if (failure != null) {
                error.setText("保存失败，记录可能已被修改，请刷新后重试");
                return;
            }
            onSaved.run();
            dispose();
        }));
    }

    @Override public void dispose() {
        asyncGuard.deactivate();
        super.dispose();
    }

    private void fill(CourseView value) {
        code.setText(value.courseCode());
        name.setText(value.courseName());
        credit.setValue(value.credit());
        hours.setValue(value.totalHours());
        description.setText(value.description());
        active.setSelected(value.active());
    }

    private static JTextField field(String accessibleName) {
        JTextField field = new JTextField();
        field.setFont(UiTypography.BODY);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        field.setPreferredSize(new Dimension(480, UiDimensions.CONTROL_HEIGHT));
        field.getAccessibleContext().setAccessibleName(accessibleName);
        return field;
    }

    private static JSpinner spinner(Number value, Comparable<?> minimum, Comparable<?> maximum,
                                    Number stepSize, String accessibleName) {
        return spinner(new SpinnerNumberModel(value, minimum, maximum, stepSize), accessibleName);
    }

    private static JSpinner creditSpinner(BigDecimal value, BigDecimal minimum, BigDecimal maximum,
                                          BigDecimal stepSize, String accessibleName) {
        return spinner(new BigDecimalSpinnerModel(value, minimum, maximum, stepSize), accessibleName);
    }

    private static JSpinner spinner(SpinnerNumberModel model, String accessibleName) {
        JSpinner spinner = new JSpinner(model);
        spinner.setFont(UiTypography.BODY);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        spinner.setPreferredSize(new Dimension(480, UiDimensions.CONTROL_HEIGHT));
        spinner.getAccessibleContext().setAccessibleName(accessibleName);
        return spinner;
    }

    private static BigDecimal decimal(Number value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static String required(JTextField field, String message) {
        String value = field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(message);
        return value;
    }

    private static JLabel label(String text, java.awt.Color color) {
        JLabel label = new JLabel(text);
        label.setFont(UiTypography.BODY);
        label.setForeground(color);
        return label;
    }

    private static final class BigDecimalSpinnerModel extends SpinnerNumberModel {
        private final BigDecimal minimum;
        private final BigDecimal maximum;
        private final BigDecimal stepSize;

        private BigDecimalSpinnerModel(BigDecimal value, BigDecimal minimum, BigDecimal maximum,
                                       BigDecimal stepSize) {
            super(value, minimum, maximum, stepSize);
            this.minimum = minimum;
            this.maximum = maximum;
            this.stepSize = stepSize;
        }

        @Override public void setValue(Object value) {
            if (!(value instanceof Number number)) throw new IllegalArgumentException("value must be numeric");
            super.setValue(decimal(number));
        }

        @Override public Object getNextValue() {
            return stepped(stepSize);
        }

        @Override public Object getPreviousValue() {
            return stepped(stepSize.negate());
        }

        private BigDecimal stepped(BigDecimal delta) {
            BigDecimal candidate = decimal(getNumber()).add(delta);
            if (candidate.compareTo(minimum) < 0 || candidate.compareTo(maximum) > 0) return null;
            return candidate;
        }
    }
}
