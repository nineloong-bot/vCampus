package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.common.student.BatchImportCommand;
import edu.seu.vcampus.common.student.BatchStudentEntry;
import edu.seu.vcampus.common.student.ClassView;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Editable preview and balanced class-allocation model. */
final class BatchAssignmentTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"姓名", "一卡通号", "性别", "综合成绩", "分配班级"};
    private final List<ClassView> classes;
    private List<Row> rows = new ArrayList<>();

    BatchAssignmentTableModel(List<ClassView> classes) { this.classes = List.copyOf(classes); }
    void setRows(List<Row> values) { rows = new ArrayList<>(values); fireTableDataChanged(); }
    boolean hasRows() { return !rows.isEmpty(); }

    void autoAssign() {
        List<Row> males = rows.stream().filter(row -> "男".equals(row.gender)).sorted(byScore()).toList();
        List<Row> females = rows.stream().filter(row -> "女".equals(row.gender)).sorted(byScore()).toList();
        assign(males, classes.size()); assign(females, classes.size()); fireTableDataChanged();
    }

    BatchImportCommand command(String majorId) {
        List<BatchStudentEntry> entries = rows.stream().map(row -> new BatchStudentEntry(
                row.card, row.name, row.gender, row.score, row.classIndex)).toList();
        return new BatchImportCommand(majorId, classes.stream().map(ClassView::classId).toList(), entries);
    }

    String summary() {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            int target = index;
            long count = rows.stream().filter(row -> row.classIndex == target).count();
            values.add(classes.get(index).name() + " " + count + "人");
        }
        return String.join("；", values);
    }

    private static Comparator<Row> byScore() { return Comparator.comparingDouble((Row row) -> row.score).reversed(); }
    private static void assign(List<Row> rows, int classCount) {
        if (classCount == 0) return;
        boolean forward = true; int index = 0;
        for (Row row : rows) {
            row.classIndex = forward ? index : classCount - 1 - index;
            if (forward && ++index >= classCount) { index = classCount - 1; forward = false; }
            else if (!forward && --index < 0) { index = 0; forward = true; }
        }
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public boolean isCellEditable(int row, int column) { return column == 4; }
    @Override public Object getValueAt(int rowIndex, int column) {
        Row row = rows.get(rowIndex);
        return switch (column) {
            case 0 -> row.name; case 1 -> row.card; case 2 -> row.gender; case 3 -> row.score;
            case 4 -> classes.get(row.classIndex).code() + " - " + classes.get(row.classIndex).name();
            default -> null;
        };
    }
    @Override public void setValueAt(Object value, int row, int column) {
        if (column != 4) return;
        for (int index = 0; index < classes.size(); index++) {
            String label = classes.get(index).code() + " - " + classes.get(index).name();
            if (label.equals(value)) rows.get(row).classIndex = index;
        }
        fireTableCellUpdated(row, column);
    }

    static final class Row {
        final String name, card, gender; final double score; int classIndex;
        Row(String name, String card, String gender, double score, int classIndex) {
            this.name = name; this.card = card; this.gender = gender;
            this.score = score; this.classIndex = classIndex;
        }
    }
}
