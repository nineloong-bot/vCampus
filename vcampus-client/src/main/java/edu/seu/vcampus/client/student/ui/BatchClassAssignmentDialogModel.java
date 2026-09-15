package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Preview table model, editors, renderers and class statistics. */
abstract class BatchClassAssignmentDialogModel extends BatchClassAssignmentDialogBase {
    List<StudentRow> rows = new ArrayList<>();
    BatchTableModel tableModel;

    BatchClassAssignmentDialogModel(java.awt.Window owner, StudentClientService students,
            MajorView major, java.util.List<ClassView> classes) {
        super(owner, students, major, classes);
    }

    void updateStats() {
        statsPanel.removeAll();
        int n = availableClasses.size();
        for (int i = 0; i < n; i++) {
            int count = 0, maleCount = 0, femaleCount = 0;
            double totalScore = 0;
            for (StudentRow r : rows) {
                if (r.classIndex == i) {
                    count++;
                    totalScore += r.score;
                    if ("男".equals(r.gender)) maleCount++; else femaleCount++;
                }
            }
            JPanel card = new JPanel(new GridLayout(0, 1));
            card.setOpaque(false);
            card.setBorder(BorderFactory.createCompoundBorder(
                    UiBorders.LINE, new javax.swing.border.EmptyBorder(UiSpacing.SPACE_1, UiSpacing.SPACE_2, UiSpacing.SPACE_1, UiSpacing.SPACE_2)));
            JLabel nameLabel = new JLabel(availableClasses.get(i).name());
            nameLabel.setFont(UiTypography.BODY);
            nameLabel.setForeground(UiColors.TEXT_PRIMARY);
            card.add(nameLabel);
            JLabel countLabel = new JLabel("人数: " + count);
            countLabel.setFont(UiTypography.CAPTION);
            countLabel.setForeground(UiColors.TEXT_SECONDARY);
            card.add(countLabel);
            String avg = count > 0 ? String.format("%.1f", totalScore / count) : "-";
            JLabel avgLabel = new JLabel("平均分: " + avg);
            avgLabel.setFont(UiTypography.CAPTION);
            avgLabel.setForeground(UiColors.TEXT_SECONDARY);
            card.add(avgLabel);
            JLabel genderLabel = new JLabel("男/女: " + maleCount + "/" + femaleCount);
            genderLabel.setFont(UiTypography.CAPTION);
            genderLabel.setForeground(UiColors.TEXT_SECONDARY);
            card.add(genderLabel);
            statsPanel.add(card);
        }
        statsPanel.revalidate();
        statsPanel.repaint();
    }

    // --- Data model ---

    /** One parsed CSV student row with its assigned class index. */
    static final class StudentRow {
        String name, campusCard, gender;
        double score;
        int classIndex;
        StudentRow(String name, String campusCard, String gender, double score, int classIndex) {
            this.name = name; this.campusCard = campusCard; this.gender = gender;
            this.score = score; this.classIndex = classIndex;
        }
    }

    /** Preview grid backed by the parsed student rows. */
    final class BatchTableModel extends AbstractTableModel {
        private final String[] COLUMNS = {"姓名", "一卡通号", "性别", "综合成绩", "分配班级"};
        private List<StudentRow> data = new ArrayList<>();
        void setData(List<StudentRow> data) { this.data = data; fireTableDataChanged(); }
        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public Class<?> getColumnClass(int col) {
            return switch (col) {
                case 3 -> Double.class;
                case 4 -> Integer.class;
                default -> String.class;
            };
        }
        @Override public boolean isCellEditable(int row, int col) { return col == 4; }
        @Override public Object getValueAt(int row, int col) {
            StudentRow r = data.get(row);
            return switch (col) {
                case 0 -> r.name;
                case 1 -> r.campusCard;
                case 2 -> r.gender;
                case 3 -> r.score;
                case 4 -> r.classIndex;
                default -> null;
            };
        }
        @Override public void setValueAt(Object value, int row, int col) {
            if (col == 4 && value instanceof Integer idx) {
                data.get(row).classIndex = idx;
                fireTableCellUpdated(row, col);
                updateStats();
            }
        }
    }

    /** Combo box cell editor selecting one of the available classes. */
    final class ClassComboEditor extends AbstractCellEditor implements TableCellEditor {
        private final JComboBox<String> combo = new JComboBox<>();
        @Override public Component getTableCellEditorComponent(JTable table, Object value,
                                                               boolean isSelected, int row, int column) {
            combo.removeAllItems();
            for (ClassView cls : availableClasses) combo.addItem(cls.code() + " - " + cls.name());
            if (value instanceof Integer idx && idx >= 0 && idx < availableClasses.size()) {
                combo.setSelectedIndex(idx);
            }
            combo.addActionListener(e -> stopCellEditing());
            return combo;
        }
        @Override public Object getCellEditorValue() { return combo.getSelectedIndex(); }
    }

    /** Centered renderer formatting scores to one decimal. */
    static class ScoreRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                                 boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (value instanceof Double d) setText(String.format("%.1f", d));
            return this;
        }
    }
}
