package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiBorders;
import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.core.ui.theme.UiSpacing;
import edu.seu.vcampus.client.core.ui.theme.UiTypography;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.protocol.ResponseBody;
import edu.seu.vcampus.common.student.*;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Dialog for CSV-based batch student import with auto class distribution. */
public final class BatchClassAssignmentDialog extends JDialog {
    private final StudentClientService students;
    private final MajorView major;
    private final List<ClassView> availableClasses;
    private final AtomicLong generation = new AtomicLong();
    private boolean disposed;

    private List<StudentRow> rows = new ArrayList<>();
    private BatchTableModel tableModel;
    private JTable previewTable;
    private JPanel statsPanel;
    private JLabel errorLabel;
    private JButton importButton;
    private JButton assignButton;
    private JLabel fileLabel;

    public BatchClassAssignmentDialog(Window owner, StudentClientService students,
                                      MajorView major, List<ClassView> classes) {
        super(owner, "批量分班", ModalityType.APPLICATION_MODAL);
        this.students = Objects.requireNonNull(students);
        this.major = Objects.requireNonNull(major);
        this.availableClasses = new ArrayList<>(classes);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(build());
        setSize(960, 720);
        setLocationRelativeTo(owner);
    }

    private JPanel build() {
        JPanel page = new JPanel(new BorderLayout(0, UiSpacing.SPACE_4));
        page.setBackground(UiColors.BACKGROUND_PAGE);
        page.setBorder(UiBorders.pageInset());

        // Heading
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, UiSpacing.SPACE_1));
        heading.setOpaque(false);
        JLabel title = new JLabel("批量分班 — " + major.name());
        title.setFont(UiTypography.PAGE_TITLE);
        title.setForeground(UiColors.TEXT_PRIMARY);
        heading.add(title);
        JLabel hint = new JLabel("CSV格式: 姓名,一卡通号,性别,综合成绩  |  初始密码: 12345678");
        hint.setFont(UiTypography.CAPTION);
        hint.setForeground(UiColors.TEXT_SECONDARY);
        heading.add(hint);
        page.add(heading, BorderLayout.NORTH);

        // Center: file chooser + table + stats
        JPanel center = new JPanel(new BorderLayout(0, UiSpacing.SPACE_3));
        center.setOpaque(false);

        // File chooser row
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, UiSpacing.SPACE_2, 0));
        fileRow.setOpaque(false);
        JButton chooseFile = new JButton("选择CSV文件");
        chooseFile.setFont(UiTypography.BODY);
        chooseFile.addActionListener(e -> chooseFile());
        fileRow.add(chooseFile);
        fileLabel = new JLabel("未选择文件");
        fileLabel.setFont(UiTypography.BODY);
        fileLabel.setForeground(UiColors.TEXT_SECONDARY);
        fileRow.add(fileLabel);
        center.add(fileRow, BorderLayout.NORTH);

        // Preview table
        tableModel = new BatchTableModel();
        previewTable = new JTable(tableModel);
        previewTable.setName("student.batch.table");
        previewTable.setFont(UiTypography.BODY);
        previewTable.setRowHeight(28);
        previewTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        previewTable.getTableHeader().setFont(UiTypography.CAPTION);
        previewTable.getTableHeader().setReorderingAllowed(false);
        previewTable.setDefaultRenderer(Double.class, new ScoreRenderer());
        previewTable.setDefaultRenderer(Integer.class, new ScoreRenderer());

        // Class column with combo box editor
        if (!availableClasses.isEmpty()) {
            previewTable.getColumnModel().getColumn(4).setCellEditor(new ClassComboEditor());
        }
        previewTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        JScrollPane tableScroll = new JScrollPane(previewTable);
        tableScroll.setPreferredSize(new Dimension(0, 350));
        center.add(tableScroll, BorderLayout.CENTER);

        // Stats panel
        statsPanel = new JPanel(new GridLayout(1, 0, UiSpacing.SPACE_3, 0));
        statsPanel.setOpaque(false);
        center.add(statsPanel, BorderLayout.SOUTH);

        page.add(center, BorderLayout.CENTER);

        // Bottom: error + buttons
        JPanel bottom = new JPanel(new BorderLayout(UiSpacing.SPACE_3, 0));
        bottom.setOpaque(false);
        errorLabel = new JLabel(" ");
        errorLabel.setName("student.batch.error");
        errorLabel.setForeground(UiColors.ERROR_FG);
        errorLabel.setFont(UiTypography.CAPTION);
        bottom.add(errorLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiSpacing.SPACE_2, 0));
        buttons.setOpaque(false);
        assignButton = new JButton("自动分配");
        assignButton.setFont(UiTypography.BODY);
        assignButton.setEnabled(false);
        assignButton.addActionListener(e -> autoAssign());
        buttons.add(assignButton);
        JButton cancel = new JButton("取消");
        cancel.setFont(UiTypography.BODY);
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        importButton = new JButton("确认导入");
        importButton.setFont(UiTypography.BODY);
        importButton.setEnabled(false);
        importButton.addActionListener(e -> doImport());
        buttons.add(importButton);
        bottom.add(buttons, BorderLayout.EAST);
        page.add(bottom, BorderLayout.SOUTH);

        return page;
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV文件 (*.csv)", "csv"));
        fc.setDialogTitle("选择学生数据CSV文件");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            List<StudentRow> parsed = parseCsv(file);
            this.rows = parsed;
            fileLabel.setText(file.getName() + " (" + parsed.size() + " 条记录)");
            fileLabel.setForeground(UiColors.TEXT_PRIMARY);
            tableModel.setData(rows);
            assignButton.setEnabled(!rows.isEmpty() && availableClasses.size() >= 2);
            importButton.setEnabled(false);
            errorLabel.setText(" ");
            updateStats();
        } catch (Exception ex) {
            errorLabel.setText("CSV解析失败: " + ex.getMessage());
        }
    }

    private static List<StudentRow> parseCsv(File file) throws Exception {
        List<StudentRow> rows = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new IllegalArgumentException("文件为空");
            // Validate header
            String[] headerCols = header.split(",", -1);
            if (headerCols.length < 4) throw new IllegalArgumentException("至少需要4列: 姓名,一卡通号,性别,综合成绩");

            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);
                if (cols.length < 4) throw new IllegalArgumentException("第" + lineNum + "行: 列数不足");
                String name = cols[0].trim();
                String campusCard = cols[1].trim();
                String gender = cols[2].trim();
                String scoreStr = cols[3].trim();
                if (name.isEmpty()) throw new IllegalArgumentException("第" + lineNum + "行: 姓名为空");
                if (campusCard.isEmpty()) throw new IllegalArgumentException("第" + lineNum + "行: 一卡通号为空");
                if (!"男".equals(gender) && !"女".equals(gender))
                    throw new IllegalArgumentException("第" + lineNum + "行: 性别必须为'男'或'女'");
                double score;
                try { score = Double.parseDouble(scoreStr); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("第" + lineNum + "行: 成绩格式错误"); }
                rows.add(new StudentRow(name, campusCard, gender, score, 0));
            }
        }
        return rows;
    }

    private void autoAssign() {
        if (rows.isEmpty() || availableClasses.size() < 2) return;
        int n = availableClasses.size();
        List<StudentRow> males = new ArrayList<>();
        List<StudentRow> females = new ArrayList<>();
        for (StudentRow r : rows) {
            if ("男".equals(r.gender)) males.add(r); else females.add(r);
        }
        males.sort(Comparator.comparingDouble((StudentRow r) -> r.score).reversed());
        females.sort(Comparator.comparingDouble((StudentRow r) -> r.score).reversed());
        // Snake-round assignment within each gender group
        assignSnakeRound(males, n);
        assignSnakeRound(females, n);
        tableModel.fireTableDataChanged();
        updateStats();
        importButton.setEnabled(true);
        errorLabel.setText(" ");
    }

    private static void assignSnakeRound(List<StudentRow> group, int classCount) {
        boolean forward = true;
        int idx = 0;
        for (StudentRow row : group) {
            row.classIndex = forward ? idx : (classCount - 1 - idx);
            if (forward) {
                idx++;
                if (idx >= classCount) { idx = classCount - 1; forward = false; }
            } else {
                idx--;
                if (idx < 0) { idx = 0; forward = true; }
            }
        }
    }

    private void updateStats() {
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

    private void doImport() {
        if (rows.isEmpty()) return;
        List<String> classIds = availableClasses.stream().map(ClassView::classId).toList();
        List<BatchStudentEntry> entries = new ArrayList<>();
        for (StudentRow r : rows) {
            entries.add(new BatchStudentEntry(r.campusCard, r.name, r.gender, r.score, r.classIndex));
        }
        BatchImportCommand command = new BatchImportCommand(major.majorId(), classIds, entries);
        long current = generation.incrementAndGet();
        importButton.setEnabled(false);
        assignButton.setEnabled(false);
        errorLabel.setText("正在导入...");
        CompletableFuture<ResponseBody<BatchImportResult>> response;
        try { response = students.batchImport(command); }
        catch (RuntimeException f) { response = CompletableFuture.failedFuture(f); }
        response.whenComplete((body, failure) -> SwingUtilities.invokeLater(() -> {
            if (disposed || current != generation.get()) return;
            if (failure != null || body == null || !body.success() || body.data() == null) {
                importButton.setEnabled(true);
                assignButton.setEnabled(true);
                errorLabel.setText(body != null && body.message() != null ? body.message() : "导入失败，请稍后重试");
                return;
            }
            showResult(body.data());
        }));
    }

    private void showResult(BatchImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><h3>导入完成</h3>");
        sb.append("成功: ").append(result.totalCreated()).append(" 条<br>");
        sb.append("失败: ").append(result.totalFailed()).append(" 条<br>");
        if (!result.errors().isEmpty()) {
            sb.append("<br><b>错误详情:</b><br>");
            for (String err : result.errors()) {
                sb.append(err).append("<br>");
            }
        }
        sb.append("</html>");
        JLabel msg = new JLabel(sb.toString());
        msg.setFont(UiTypography.BODY);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiColors.BACKGROUND_PAGE);
        panel.setBorder(UiBorders.pageInset());
        panel.add(msg, BorderLayout.CENTER);
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bp.setOpaque(false);
        bp.add(close);
        panel.add(bp, BorderLayout.SOUTH);
        setContentPane(panel);
        revalidate();
        repaint();
    }

    @Override
    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
        super.dispose();
    }

    // --- Data model ---

    static final class StudentRow {
        String name, campusCard, gender;
        double score;
        int classIndex;
        StudentRow(String name, String campusCard, String gender, double score, int classIndex) {
            this.name = name; this.campusCard = campusCard; this.gender = gender;
            this.score = score; this.classIndex = classIndex;
        }
    }

    private class BatchTableModel extends AbstractTableModel {
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

    private class ClassComboEditor extends AbstractCellEditor implements TableCellEditor {
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

    private static class ScoreRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                                 boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (value instanceof Double d) setText(String.format("%.1f", d));
            return this;
        }
    }
}
