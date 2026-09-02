package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiDimensions;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Structured editor for one or more offering schedule rows. */
public final class OfferingScheduleEditorPanel extends JPanel {
    private final JPanel rowsPanel = new JPanel();
    private final List<ScheduleRow> rows = new ArrayList<>();

    public OfferingScheduleEditorPanel() {
        super(new BorderLayout(0, UiSpacing.SM));
        setOpaque(false);
        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        add(rowsPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        JButton add = AbstractCoursePanel.secondary("添加上课时间");
        add.addActionListener(event -> addDefaultRow());
        actions.add(add);
        add(actions, BorderLayout.SOUTH);
    }

    /** Replaces all rows with the supplied aggregate schedule values. */
    public void setSchedules(List<ScheduleItem> schedules) {
        rows.clear();
        rowsPanel.removeAll();
        for (ScheduleItem item : schedules) addRow(new ScheduleRow(item));
        revalidate();
        repaint();
    }

    /** Adds a localized row with the standard Monday, periods 1-2, weeks 1-16 default. */
    public void addDefaultRow() {
        addRow(new ScheduleRow());
        revalidate();
        repaint();
    }

    /** Maps controls directly to typed protocol values without parsing a CSV intermediary. */
    public List<CreateOfferingCommand.ScheduleInput> scheduleInputs() {
        if (rows.isEmpty()) throw new IllegalArgumentException("请至少添加一行上课时间");
        List<CreateOfferingCommand.ScheduleInput> inputs = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) inputs.add(rows.get(index).toInput(index + 1));
        return List.copyOf(inputs);
    }

    private void addRow(ScheduleRow row) {
        rows.add(row);
        renderRows();
    }

    private void removeRow(ScheduleRow row) {
        if (!rows.remove(row)) return;
        renderRows();
        revalidate();
        repaint();
    }

    private void renderRows() {
        rowsPanel.removeAll();
        for (ScheduleRow row : rows) {
            rowsPanel.add(row);
            rowsPanel.add(Box.createVerticalStrut(UiSpacing.SM));
        }
        renameRows();
    }

    private void renameRows() {
        for (int index = 0; index < rows.size(); index++) rows.get(index).setRowNumber(index + 1);
    }

    private final class ScheduleRow extends JPanel {
        private final JComboBox<WeekdayChoice> day = new JComboBox<>(WeekdayChoice.values());
        private final JSpinner startPeriod = spinner(1, 1, 14);
        private final JSpinner endPeriod = spinner(2, 1, 14);
        private final JSpinner startWeek = spinner(1, 1, 30);
        private final JSpinner endWeek = spinner(16, 1, 30);
        private final JTextField classroom = field("待定", 120);
        private final JButton remove = AbstractCoursePanel.secondary("删除");

        private ScheduleRow() {
            this(null);
        }

        private ScheduleRow(ScheduleItem item) {
            super(new FlowLayout(FlowLayout.LEFT, UiSpacing.SM, UiSpacing.SM));
            setOpaque(true);
            setBackground(UiColors.BACKGROUND_SUBTLE);
            setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
            configure(day, 92);
            add(control("星期", day));
            add(control("起始节", startPeriod));
            add(control("结束节", endPeriod));
            add(control("起始周", startWeek));
            add(control("结束周", endWeek));
            add(control("教室", classroom));
            remove.addActionListener(event -> removeRow(this));
            add(control("操作", remove));
            if (item != null) {
                day.setSelectedItem(WeekdayChoice.fromCode(item.dayOfWeek()));
                startPeriod.setValue(item.startPeriod());
                endPeriod.setValue(item.endPeriod());
                startWeek.setValue(item.startWeek());
                endWeek.setValue(item.endWeek());
                classroom.setText(item.classroom());
            }
        }

        private void setRowNumber(int rowNumber) {
            day.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行星期");
            startPeriod.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行起始节次");
            endPeriod.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行结束节次");
            startWeek.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行起始周");
            endWeek.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行结束周");
            classroom.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行教室");
            remove.setText("删除第 " + rowNumber + " 行");
        }

        private CreateOfferingCommand.ScheduleInput toInput(int rowNumber) {
            int startP = number(startPeriod);
            int endP = number(endPeriod);
            int startW = number(startWeek);
            int endW = number(endWeek);
            if (startP > endP) {
                throw new IllegalArgumentException("第 " + rowNumber + " 行：结束节次不能早于起始节次");
            }
            if (startW > endW) {
                throw new IllegalArgumentException("第 " + rowNumber + " 行：结束周不能早于起始周");
            }
            String room = classroom.getText().trim();
            if (room.isEmpty()) throw new IllegalArgumentException("第 " + rowNumber + " 行：请输入教室");
            WeekdayChoice selectedDay = (WeekdayChoice) day.getSelectedItem();
            return new CreateOfferingCommand.ScheduleInput(selectedDay.code(), startP, endP, startW, endW, room);
        }
    }

    private static JPanel control(String caption, java.awt.Component component) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(caption);
        label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY);
        panel.add(label);
        panel.add(Box.createVerticalStrut(2));
        panel.add(component);
        return panel;
    }

    private static JSpinner spinner(int value, int minimum, int maximum) {
        JSpinner spinner = new JSpinner(new BoundedIntegerSpinnerModel(value, minimum, maximum));
        configure(spinner, 58);
        return spinner;
    }

    private static JTextField field(String value, int width) {
        JTextField field = new JTextField(value);
        field.setFont(UiTypography.BODY);
        configure(field, width);
        return field;
    }

    private static void configure(javax.swing.JComponent component, int width) {
        component.setFont(UiTypography.BODY);
        component.setPreferredSize(new Dimension(width, UiDimensions.CONTROL_HEIGHT));
        component.setMaximumSize(new Dimension(width, UiDimensions.CONTROL_HEIGHT));
    }

    private static int number(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private enum WeekdayChoice {
        MONDAY("MONDAY", "周一"), TUESDAY("TUESDAY", "周二"), WEDNESDAY("WEDNESDAY", "周三"),
        THURSDAY("THURSDAY", "周四"), FRIDAY("FRIDAY", "周五"), SATURDAY("SATURDAY", "周六"),
        SUNDAY("SUNDAY", "周日");

        private final String code;
        private final String label;

        WeekdayChoice(String code, String label) {
            this.code = code;
            this.label = label;
        }

        String code() { return code; }

        static WeekdayChoice fromCode(String code) {
            for (WeekdayChoice value : values()) if (value.code.equalsIgnoreCase(code)) return value;
            throw new IllegalArgumentException("不支持的星期：" + code);
        }

        @Override public String toString() { return label; }
    }

    private static final class BoundedIntegerSpinnerModel extends SpinnerNumberModel {
        private final int minimum;
        private final int maximum;

        private BoundedIntegerSpinnerModel(int value, int minimum, int maximum) {
            super(value, minimum, maximum, 1);
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override public void setValue(Object value) {
            if (!(value instanceof Number number)) throw new IllegalArgumentException("数值必须是整数");
            int candidate = number.intValue();
            if (candidate < minimum || candidate > maximum) {
                throw new IllegalArgumentException("数值必须在 " + minimum + " 到 " + maximum + " 之间");
            }
            super.setValue(candidate);
        }
    }
}
