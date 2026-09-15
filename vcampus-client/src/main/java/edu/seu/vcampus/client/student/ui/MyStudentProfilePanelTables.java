package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.network.ClientConnection;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;

import javax.swing.*;
import java.awt.*;

/** Builds the read-only profile table and the editable personal grid. */
abstract class MyStudentProfilePanelTables extends MyStudentProfilePanelExporting {

    /** Creates the tables segment of the profile panel. */
    protected MyStudentProfilePanelTables(StudentClientService students, ClientConnection connection) {
        super(students, connection);
    }

    JPanel buildPersonalEditTable(String[][] definitions) {
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        int rows = (definitions.length + 2) / 3;
        for (int index = 0; index < rows * 3; index++) {
            int row = index / 3, pair = index % 3;
            String key = index < definitions.length ? definitions[index][0] : null;
            String title = index < definitions.length ? definitions[index][1] : "";
            if (key == null) {
                JLabel emptyLabel = cell("", true);
                JLabel emptyValue = cell("", false);
                addCell(table, emptyLabel, pair * 2, row, .12);
                addCell(table, emptyValue, pair * 2 + 1, row, .21);
                continue;
            }

            boolean isCore = CORE_READONLY_KEYS.contains(key);
            String labelText = (!isCore && REQUIRED_EDIT_KEYS.contains(key))
                    ? "<html><font color='#e53935'>* </font>" + title + "</html>"
                    : title;
            JLabel label = cell(labelText, true);
            addCell(table, label, pair * 2, row, .12);

            if (isCore) {
                JLabel valueLabel = cell("未填写", false);
                valueLabel.setName("student.profile.edit." + key);
                editReadOnlyLabels.put(key, valueLabel);
                addCell(table, valueLabel, pair * 2 + 1, row, .21);
            } else if (isComboField(key)) {
                String[] options = getComboOptions(key);
                JComboBox<String> combo = new JComboBox<>(options);
                combo.setBackground(Color.WHITE);
                combo.setFont(UiTypography.BODY);
                combo.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(TABLE_BORDER),
                        BorderFactory.createEmptyBorder(2, 4, 2, 4)));
                combo.setMinimumSize(new Dimension(140, 38));
                combo.setPreferredSize(new Dimension(140, 38));
                combo.setName("student.profile.personal." + key);
                combo.setToolTipText(PersonalProfileEditPanel.HINTS.get(key));
                combo.getAccessibleContext().setAccessibleName(title);
                editComponents.put(key, combo);
                addCell(table, combo, pair * 2 + 1, row, .21);
            } else {
                JTextField field = new JTextField();
                field.setBackground(Color.WHITE);
                field.setFont(UiTypography.BODY);
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(TABLE_BORDER),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
                field.setMinimumSize(new Dimension(140, 38));
                field.setPreferredSize(new Dimension(140, 38));
                field.setName("student.profile.personal." + key);
                field.setToolTipText(PersonalProfileEditPanel.HINTS.get(key));
                field.getAccessibleContext().setAccessibleName(title);
                editComponents.put(key, field);
                addCell(table, field, pair * 2 + 1, row, .21);
            }
        }

        wireLinkages();
        return table;
    }

    void wireLinkages() {
        JComboBox<?> polCombo = (JComboBox<?>) editComponents.get("politicalStatus");
        JComboBox<?> legCombo = (JComboBox<?>) editComponents.get("leagueMember");
        JTextField legDate = (JTextField) editComponents.get("leagueJoinDate");
        JComboBox<?> ptyCombo = (JComboBox<?>) editComponents.get("partyMember");
        JTextField ptyDate = (JTextField) editComponents.get("partyJoinDate");

        if (polCombo != null) {
            polCombo.addActionListener(e -> {
                String selected = (String) polCombo.getSelectedItem();
                if ("群众".equals(selected)) {
                    if (legCombo != null) legCombo.setSelectedItem("否");
                    if (legDate != null) { legDate.setText(""); legDate.setEnabled(false); }
                    if (ptyCombo != null) ptyCombo.setSelectedItem("否");
                    if (ptyDate != null) { ptyDate.setText(""); ptyDate.setEnabled(false); }
                } else if ("共青团员".equals(selected)) {
                    if (legCombo != null) legCombo.setSelectedItem("是");
                    if (legDate != null) legDate.setEnabled(true);
                    if (ptyCombo != null) ptyCombo.setSelectedItem("否");
                    if (ptyDate != null) { ptyDate.setText(""); ptyDate.setEnabled(false); }
                } else if ("中共党员".equals(selected) || "中共预备党员".equals(selected)) {
                    if (ptyCombo != null) ptyCombo.setSelectedItem("是");
                    if (ptyDate != null) ptyDate.setEnabled(true);
                }
            });
        }
        if (legCombo != null && legDate != null) {
            legCombo.addActionListener(e -> {
                boolean isYes = "是".equals(legCombo.getSelectedItem());
                legDate.setEnabled(isYes);
                if (!isYes) legDate.setText("");
            });
        }
        if (ptyCombo != null && ptyDate != null) {
            ptyCombo.addActionListener(e -> {
                boolean isYes = "是".equals(ptyCombo.getSelectedItem());
                ptyDate.setEnabled(isYes);
                if (!isYes) ptyDate.setText("");
            });
        }
    }

    JPanel profileTable(String[][] definitions) {
        JPanel table = new JPanel(new GridBagLayout()); table.setOpaque(false); table.setAlignmentX(Component.LEFT_ALIGNMENT);
        int rows = (definitions.length + 2) / 3;
        for (int index = 0; index < rows * 3; index++) {
            int row = index / 3, pair = index % 3; String key = index < definitions.length ? definitions[index][0] : null;
            String title = index < definitions.length ? definitions[index][1] : "";
            JLabel label = cell(title, true); JLabel value = cell("未填写", false);
            if (key != null) { value.setName("student.profile." + key); values.put(key, value); }
            addCell(table, label, pair * 2, row, .12); addCell(table, value, pair * 2 + 1, row, .21);
        }
        return table;
    }
}
