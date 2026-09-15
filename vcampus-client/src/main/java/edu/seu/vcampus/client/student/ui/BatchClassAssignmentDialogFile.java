package edu.seu.vcampus.client.student.ui;

import edu.seu.vcampus.client.core.ui.theme.UiColors;
import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.ClassView;
import edu.seu.vcampus.common.student.MajorView;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** CSV selection and parsing for the batch assignment segments. */
abstract class BatchClassAssignmentDialogFile extends BatchClassAssignmentDialogModel {

    BatchClassAssignmentDialogFile(java.awt.Window owner, StudentClientService students,
            MajorView major, java.util.List<ClassView> classes) {
        super(owner, students, major, classes);
    }

    void chooseFile() {
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
}
