package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.*;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import java.awt.*;

/** Profile grids and the change-history table for the student detail panel segments. */
abstract class StudentDetailPanelTable extends StudentDetailPanelBase {

    StudentDetailPanelTable(StudentClientService students,
            edu.seu.vcampus.client.core.network.ClientConnection connection,
            String studentId, boolean canEdit) {
        super(students, connection, studentId, canEdit);
    }

    JPanel profileTable(String[][] definitions) {
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        int rows = (definitions.length + 2) / 3;
        for (int index = 0; index < rows * 3; index++) {
            int row = index / 3;
            int pair = index % 3;
            String key = index < definitions.length ? definitions[index][0] : null;
            String title = index < definitions.length ? definitions[index][1] : "";
            JLabel label = key == null ? emptyCell() : cell(title, true);
            JLabel value = key == null ? emptyCell() : cell("未填写", false);
            if (key != null) {
                value.setName("student.detail.profile." + key);
                values.put(key, value);
            }
            addCell(table, label, pair * 2, row, .12);
            addCell(table, value, pair * 2 + 1, row, .21);
        }
        return table;
    }

    JScrollPane changesTable() {
        changesTable = new JTable(changesModel) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (c instanceof JComponent jc) {
                    Object val = getValueAt(row, column);
                    jc.setToolTipText(val != null ? val.toString() : null);
                }
                return c;
            }
        };
        changesTable.setName("student.detail.changes");
        changesTable.setFont(UiTypography.BODY);
        changesTable.setRowHeight(UiSpacing.SPACE_6);
        changesTable.getTableHeader().setFont(UiTypography.CAPTION);
        changesTable.getTableHeader().setReorderingAllowed(false);
        changesTable.getAccessibleContext().setAccessibleName("变更记录");
        changesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        changesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) openChangeDetail();
            }
        });
        JScrollPane scroll = new JScrollPane(changesTable);
        scroll.setName("student.detail.changes.scroll");
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(0, 200));
        scroll.setBorder(UiBorders.LINE);
        return scroll;
    }

    private void openChangeDetail() {
        StudentChangeView change = changesModel.getChangeAt(changesTable.getSelectedRow());
        if (change != null) new ChangeDetailDialog(SwingUtilities.getWindowAncestor(this), change).setVisible(true);
    }

    private static JLabel emptyCell() {
        JLabel empty = new JLabel();
        empty.setOpaque(false);
        return empty;
    }

    private static void addCell(JPanel table, JComponent cell, int x, int y, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = weight;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.anchor = GridBagConstraints.WEST;
        table.add(cell, constraints);
    }

    private static JLabel cell(String value, boolean label) {
        JLabel result = text(value, label ? UiTypography.BODY.deriveFont(Font.BOLD) : UiTypography.BODY,
                UiColors.TEXT_PRIMARY);
        result.setOpaque(true);
        result.setBackground(label ? TABLE_LABEL : Color.WHITE);
        result.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(TABLE_BORDER),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        result.setMinimumSize(new Dimension(label ? 105 : 140, 38));
        if (value != null && !value.isBlank()) {
            result.setToolTipText(value);
        }
        return result;
    }
}
