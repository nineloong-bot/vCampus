package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.common.student.AttendanceMode;

import javax.swing.*;
import java.awt.*;

/** The intentionally narrow academic editor: only attendance mode is mutable. */
public final class AttendanceModeEditPanel extends JPanel {
    private final JComboBox<AttendanceMode> mode;

    public AttendanceModeEditPanel(AttendanceMode selected) {
        super(new GridBagLayout());
        setBackground(UiColors.BACKGROUND_PAGE);
        setBorder(BorderFactory.createEmptyBorder(UiSpacing.SPACE_6, UiSpacing.SPACE_6,
                UiSpacing.SPACE_6, UiSpacing.SPACE_6));
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0; label.gridy = 0; label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(0, 0, 0, UiSpacing.SPACE_4);
        JLabel title = new JLabel("* 就读方式");
        title.setFont(UiTypography.BODY.deriveFont(Font.BOLD));
        title.setForeground(UiColors.TEXT_PRIMARY);
        add(title, label);

        mode = new JComboBox<>(AttendanceMode.values());
        mode.setName("student.profile.attendance.mode");
        mode.getAccessibleContext().setAccessibleName("就读方式");
        mode.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setText(value instanceof AttendanceMode item ? item.displayName() : "");
                return this;
            }
        });
        mode.setSelectedItem(selected == null ? AttendanceMode.RESIDENT : selected);
        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1; field.gridy = 0; field.weightx = 1; field.fill = GridBagConstraints.HORIZONTAL;
        field.ipady = UiSpacing.SPACE_2; add(mode, field);
    }

    public AttendanceMode selectedMode() { return (AttendanceMode) mode.getSelectedItem(); }
}
