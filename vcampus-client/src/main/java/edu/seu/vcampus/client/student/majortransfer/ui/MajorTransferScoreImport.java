package edu.seu.vcampus.client.student.majortransfer.ui;

import edu.seu.vcampus.client.student.service.StudentClientService;
import edu.seu.vcampus.common.student.majortransfer.ImportMajorTransferScoresCommand;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class MajorTransferScoreImport {
    private MajorTransferScoreImport() { }

    static void choose(Component parent, StudentClientService students,
            String optionId, Runnable completed) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "成绩文件 (*.csv, *.xlsx)", "csv", "xlsx"));
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try {
            List<ImportMajorTransferScoresCommand.ScoreEntry> entries =
                    parse(chooser.getSelectedFile());
            students.importTransferScores(new ImportMajorTransferScoresCommand(
                    optionId, entries, 0)).whenComplete((response, failure) ->
                    SwingUtilities.invokeLater(() -> {
                        if (response != null && response.success()) {
                            JOptionPane.showMessageDialog(parent, "导入完成：成功 "
                                    + response.data().successCount() + " 条，失败 "
                                    + response.data().failureCount() + " 条");
                            completed.run();
                        } else {
                            JOptionPane.showMessageDialog(parent, response == null
                                    ? "导入失败" : response.message());
                        }
                    }));
        } catch (Exception error) {
            JOptionPane.showMessageDialog(parent, "文件解析失败：" + error.getMessage());
        }
    }

    private static List<ImportMajorTransferScoresCommand.ScoreEntry> parse(File file)
            throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".csv")) return csv(file);
        if (name.endsWith(".xlsx")) return excel(file);
        throw new IllegalArgumentException("仅支持 CSV 或 XLSX 文件");
    }

    private static List<ImportMajorTransferScoresCommand.ScoreEntry> csv(File file)
            throws Exception {
        List<ImportMajorTransferScoresCommand.ScoreEntry> entries = new ArrayList<>();
        try (var reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                if (cells.length == 0 || cells[0].isBlank()) continue;
                entries.add(new ImportMajorTransferScoresCommand.ScoreEntry(cells[0].trim(),
                        decimal(cells, 1), decimal(cells, 2)));
            }
        }
        return entries;
    }

    private static List<ImportMajorTransferScoresCommand.ScoreEntry> excel(File file)
            throws Exception {
        List<ImportMajorTransferScoresCommand.ScoreEntry> entries = new ArrayList<>();
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file)) {
            var sheet = workbook.getSheetAt(0);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null) continue;
                String id = text(row.getCell(0));
                if (!id.isBlank()) entries.add(new ImportMajorTransferScoresCommand.ScoreEntry(
                        id, number(row.getCell(1)), number(row.getCell(2))));
            }
        }
        return entries;
    }

    private static BigDecimal decimal(String[] cells, int index) {
        return index >= cells.length || cells[index].isBlank()
                ? null : new BigDecimal(cells[index].trim());
    }

    private static String text(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        return new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell).trim();
    }

    private static BigDecimal number(org.apache.poi.ss.usermodel.Cell cell) {
        String value = text(cell);
        return value.isBlank() ? null : new BigDecimal(value);
    }
}
