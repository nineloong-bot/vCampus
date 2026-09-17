package edu.seu.vcampus.client.course.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.course.CreateOfferingCommand;
import edu.seu.vcampus.common.course.ScheduleItem;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import edu.seu.vcampus.client.core.ui.autocomplete.AutocompleteSelectionField;
import edu.seu.vcampus.client.core.ui.autocomplete.SuggestionLoader;

/** One editable and removable teaching-class schedule row. */
final class OfferingScheduleRowPanel extends JPanel {
    private final JComboBox<Weekday> day = new JComboBox<>(Weekday.values());
    private final JSpinner startPeriod = spinner(1, 1, 13);
    private final JSpinner endPeriod = spinner(2, 1, 13);
    private final JSpinner startWeek = spinner(1, 1, 30);
    private final JSpinner endWeek = spinner(16, 1, 30);
    private final AutocompleteSelectionField classroom;
    private final JButton remove = AbstractCoursePanel.secondary("删除");

    OfferingScheduleRowPanel(ScheduleItem item, Consumer<OfferingScheduleRowPanel> removal,
                             SuggestionLoader classroomLoader) {
        super(new FlowLayout(FlowLayout.LEFT, UiSpacing.SM, UiSpacing.SM));
        classroom = new AutocompleteSelectionField(classroomLoader);
        configure(classroom, 200);
        setOpaque(true);
        setBackground(UiColors.BACKGROUND_SUBTLE);
        setBorder(BorderFactory.createLineBorder(UiColors.BORDER_DEFAULT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        configure(day, 92);
        add(control("星期", day)); add(control("起始节", startPeriod));
        add(control("结束节", endPeriod)); add(control("起始周", startWeek));
        add(control("结束周", endWeek)); add(control("教室", classroom));
        remove.addActionListener(event -> removal.accept(this));
        add(control("操作", remove));
        if (item != null) load(item);
    }

    void setRowNumber(int rowNumber) {
        day.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行星期");
        startPeriod.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行起始节次");
        endPeriod.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行结束节次");
        startWeek.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行起始周");
        endWeek.getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行结束周");
        classroom.inputComponent().getAccessibleContext().setAccessibleName("第 " + rowNumber + " 行教室");
        remove.setText("删除");
        remove.getAccessibleContext().setAccessibleName("删除第 " + rowNumber + " 行");
    }

    CreateOfferingCommand.ScheduleInput toInput(int rowNumber) {
        int startP = number(startPeriod); int endP = number(endPeriod);
        int startW = number(startWeek); int endW = number(endWeek);
        if (startP > endP) throw new IllegalArgumentException(
                "第 " + rowNumber + " 行：结束节次不能早于起始节次");
        if (startW > endW) throw new IllegalArgumentException(
                "第 " + rowNumber + " 行：结束周不能早于起始周");
        String room;
        try { room = classroom.requireSelection().id(); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("第 " + rowNumber + " 行：请从匹配结果中选择教室");
        }
        Weekday selected = (Weekday) day.getSelectedItem();
        return new CreateOfferingCommand.ScheduleInput(selected.code, startP, endP, startW, endW, room);
    }

    String fingerprint() {
        Weekday selected = (Weekday) day.getSelectedItem();
        return String.join("\u0000", selected == null ? "" : selected.code,
                startPeriod.getValue().toString(), endPeriod.getValue().toString(),
                startWeek.getValue().toString(), endWeek.getValue().toString(),
                classroom.selectedId().orElse(classroom.inputComponent().getText()));
    }

    void setClassroom(String value) { classroom.setSelection(value, value); }

    private void load(ScheduleItem item) {
        day.setSelectedItem(Weekday.from(item.dayOfWeek()));
        startPeriod.setValue(item.startPeriod()); endPeriod.setValue(item.endPeriod());
        startWeek.setValue(item.startWeek()); endWeek.setValue(item.endWeek());
        classroom.setSelection(item.classroom(), item.classroom());
    }

    private static JPanel control(String caption, Component component) {
        JPanel panel = new JPanel(); panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(caption); label.setFont(UiTypography.CAPTION);
        label.setForeground(UiColors.TEXT_SECONDARY); panel.add(label);
        panel.add(Box.createVerticalStrut(2)); panel.add(component); return panel;
    }

    private static JSpinner spinner(int value, int minimum, int maximum) {
        JSpinner spinner = new JSpinner(new BoundedModel(value, minimum, maximum));
        configure(spinner, 58); return spinner;
    }

    private static JTextField field(String value, int width) {
        JTextField field = new JTextField(value); configure(field, width); return field;
    }

    private static void configure(JComponent component, int width) {
        component.setFont(UiTypography.BODY);
        component.setPreferredSize(new Dimension(width, UiDimensions.CONTROL_HEIGHT));
        component.setMaximumSize(new Dimension(width, UiDimensions.CONTROL_HEIGHT));
    }

    private static int number(JSpinner spinner) { return ((Number) spinner.getValue()).intValue(); }

    private enum Weekday {
        MONDAY("MONDAY", "周一"), TUESDAY("TUESDAY", "周二"), WEDNESDAY("WEDNESDAY", "周三"),
        THURSDAY("THURSDAY", "周四"), FRIDAY("FRIDAY", "周五"), SATURDAY("SATURDAY", "周六"),
        SUNDAY("SUNDAY", "周日");
        private final String code; private final String label;
        Weekday(String code, String label) { this.code = code; this.label = label; }
        static Weekday from(String code) {
            for (Weekday value : values()) if (value.code.equalsIgnoreCase(code)) return value;
            throw new IllegalArgumentException("不支持的星期：" + code);
        }
        @Override public String toString() { return label; }
    }

    private static final class BoundedModel extends SpinnerNumberModel {
        private final int minimum; private final int maximum;
        private BoundedModel(int value, int minimum, int maximum) {
            super(value, minimum, maximum, 1); this.minimum = minimum; this.maximum = maximum;
        }
        @Override public void setValue(Object value) {
            if (!(value instanceof Number number)) throw new IllegalArgumentException("数值必须是整数");
            int candidate = number.intValue();
            if (candidate < minimum || candidate > maximum) throw new IllegalArgumentException(
                    "数值必须在 " + minimum + " 到 " + maximum + " 之间");
            super.setValue(candidate);
        }
    }
}
