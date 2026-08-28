package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.OfferingSummary;
import edu.seu.vcampus.common.course.OfferingView;
import edu.seu.vcampus.common.course.ScheduleItem;
import edu.seu.vcampus.common.course.UpdateOfferingCommand;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** Modal create/edit form for an offering aggregate and all of its schedule rows. */
final class OfferingEditorDialog extends JDialog {
    private final CourseUiGateway gateway;
    private final OfferingSummary existing;
    private final Runnable onSaved;
    private final JTextField termId = field("学期编号");
    private final JTextField courseId = field("课程编号");
    private final JTextField teacherId = field("教师用户编号");
    private final JTextField className = field("教学班名称");
    private final JTextField capacity = field("容量");
    private final JComboBox<String> status = new JComboBox<>(new String[]{"DRAFT", "OPEN", "CLOSED", "CANCELLED"});
    private final JTextArea schedules = new JTextArea(5, 48);
    private final JLabel error = label(" ", UiColors.ACCENT);
    private final JButton save;

    OfferingEditorDialog(Window owner, CourseUiGateway gateway, OfferingSummary existing, Runnable onSaved) {
        super(owner, existing == null ? "新建教学班" : "编辑教学班", ModalityType.APPLICATION_MODAL);
        this.gateway = gateway;
        this.existing = existing;
        this.onSaved = onSaved;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, UiSpacing.LG));
        root.setBackground(UiColors.BACKGROUND_PAGE);
        root.setBorder(BorderFactory.createEmptyBorder(UiSpacing.XL, UiSpacing.XL, UiSpacing.XL, UiSpacing.XL));
        root.add(title(), BorderLayout.NORTH);
        root.add(form(), BorderLayout.CENTER);
        save = AbstractCoursePanel.primary(existing == null ? "创建教学班" : "保存修改");
        save.addActionListener(event -> submit());
        root.add(actions(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(save);
        if (existing != null) fill(existing);
        setSize(new Dimension(680, 690));
        setLocationRelativeTo(owner);
    }

    private JPanel title() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel heading = label(existing == null ? "新建教学班" : "编辑教学班", UiColors.TEXT_PRIMARY);
        heading.setFont(UiTypography.PAGE_TITLE);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label("维护教师、容量、开放状态以及一行或多行上课安排", UiColors.TEXT_SECONDARY));
        return panel;
    }

    private JPanel form() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(pair("学期编号（必填）", termId, "课程编号（必填）", courseId));
        panel.add(pair("教师用户编号（必填）", teacherId, "教学班名称（必填）", className));
        status.setFont(UiTypography.BODY);
        status.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        status.getAccessibleContext().setAccessibleName("教学班状态");
        panel.add(pair("容量（必填）", capacity, "教学班状态", status));
        panel.add(label("上课安排（每行：星期,起始节,结束节,起始周,结束周,教室）", UiColors.TEXT_PRIMARY));
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        schedules.setFont(UiTypography.BODY);
        schedules.setLineWrap(false);
        schedules.getAccessibleContext().setAccessibleName("上课安排");
        schedules.setToolTipText("示例：MONDAY,1,2,1,16,教一-201");
        JScrollPane scroll = new JScrollPane(schedules);
        scroll.setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(UiSpacing.SM));
        panel.add(label("星期支持 MONDAY–SUNDAY 或周一–周日；可填写多行", UiColors.TEXT_SECONDARY));
        return panel;
    }

    private JPanel pair(String leftLabel, java.awt.Component left, String rightLabel, java.awt.Component right) {
        JPanel pair = new JPanel(new java.awt.GridLayout(1, 2, UiSpacing.LG, 0));
        pair.setOpaque(false);
        pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        pair.add(row(leftLabel, left));
        pair.add(row(rightLabel, right));
        return pair;
    }

    private JPanel row(String text, java.awt.Component input) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        JLabel caption = label(text, UiColors.TEXT_PRIMARY);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        row.add(caption);
        row.add(Box.createVerticalStrut(UiSpacing.SM));
        if (input instanceof javax.swing.JComponent component) component.setAlignmentX(LEFT_ALIGNMENT);
        row.add(input);
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
        CompletableFuture<OfferingView> request;
        try {
            String cleanTerm = required(termId, "请输入学期编号");
            String cleanCourse = required(courseId, "请输入课程编号");
            String cleanTeacher = required(teacherId, "请输入教师用户编号");
            String cleanClass = required(className, "请输入教学班名称");
            int cleanCapacity = Integer.parseInt(required(capacity, "请输入容量"));
            if (existing != null && cleanCapacity < existing.enrolledCount()) {
                throw new IllegalArgumentException("容量不能小于当前已选人数 " + existing.enrolledCount());
            }
            String cleanStatus = (String) status.getSelectedItem();
            List<CreateOfferingCommand.ScheduleInput> cleanSchedules = parseSchedules(schedules.getText());
            if (existing == null) {
                request = gateway.createOffering(new CreateOfferingCommand(cleanTerm, cleanCourse, cleanTeacher,
                        cleanClass, cleanCapacity, cleanStatus, cleanSchedules));
            } else {
                request = gateway.updateOffering(new UpdateOfferingCommand(existing.offeringId(), cleanTerm,
                        cleanCourse, cleanTeacher, cleanClass, cleanCapacity, cleanStatus,
                        existing.rowVersion(), cleanSchedules));
            }
        } catch (NumberFormatException invalid) {
            error.setText("容量、节次和周次必须填写有效整数");
            return;
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage() == null || invalid.getMessage().startsWith("invalid")
                    ? "请检查教学班字段和上课安排" : invalid.getMessage());
            return;
        }
        String idle = save.getText();
        save.setEnabled(false);
        save.setText(existing == null ? "正在创建…" : "正在保存…");
        request.whenComplete((saved, failure) -> SwingUtilities.invokeLater(() -> {
            save.setEnabled(true);
            save.setText(idle);
            if (failure != null) { error.setText("保存失败，记录可能已被修改，请刷新后重试"); return; }
            onSaved.run();
            dispose();
        }));
    }

    private void fill(OfferingSummary value) {
        termId.setText(value.termId());
        courseId.setText(value.courseId());
        teacherId.setText(value.teacherUserId());
        className.setText(value.className());
        capacity.setText(Integer.toString(value.capacity()));
        status.setSelectedItem(value.offeringStatus());
        schedules.setText(value.schedules().stream().map(OfferingEditorDialog::format).collect(Collectors.joining("\n")));
    }

    private static List<CreateOfferingCommand.ScheduleInput> parseSchedules(String value) {
        List<CreateOfferingCommand.ScheduleInput> parsed = new ArrayList<>();
        for (String raw : value.lines().toList()) {
            if (raw.isBlank()) continue;
            String[] columns = raw.split(",", 6);
            if (columns.length != 6) throw new IllegalArgumentException("每行上课安排必须包含 6 项");
            parsed.add(new CreateOfferingCommand.ScheduleInput(day(columns[0].trim()),
                    Integer.parseInt(columns[1].trim()), Integer.parseInt(columns[2].trim()),
                    Integer.parseInt(columns[3].trim()), Integer.parseInt(columns[4].trim()), columns[5].trim()));
        }
        if (parsed.isEmpty()) throw new IllegalArgumentException("请至少填写一行上课安排");
        return List.copyOf(parsed);
    }

    private static String day(String value) {
        return switch (value) {
            case "周一" -> "MONDAY"; case "周二" -> "TUESDAY"; case "周三" -> "WEDNESDAY";
            case "周四" -> "THURSDAY"; case "周五" -> "FRIDAY"; case "周六" -> "SATURDAY";
            case "周日", "周天" -> "SUNDAY"; default -> value.toUpperCase(Locale.ROOT);
        };
    }

    private static String format(ScheduleItem value) {
        return value.dayOfWeek() + "," + value.startPeriod() + "," + value.endPeriod() + ","
                + value.startWeek() + "," + value.endWeek() + "," + value.classroom();
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setFont(UiTypography.BODY);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiDimensions.CONTROL_HEIGHT));
        field.setPreferredSize(new Dimension(280, UiDimensions.CONTROL_HEIGHT));
        field.getAccessibleContext().setAccessibleName(name);
        return field;
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
}
