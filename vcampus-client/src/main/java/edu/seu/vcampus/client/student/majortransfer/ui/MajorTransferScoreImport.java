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
                            var data = response.data();
                            StringBuilder msg = new StringBuilder("导入完成：成功 ")
                                    .append(data.successCount()).append(" 条，失败 ")
                                    .append(data.failureCount()).append(" 条");
                            if (data.failureCount() > 0 && data.failures() != null && !data.failures().isEmpty()) {
                                msg.append("\n\n失败详情：");
                                for (int i = 0; i < Math.min(5, data.failures().size()); i++) {
                                    var f = data.failures().get(i);
                                    msg.append("\n• ").append(f.applicationId()).append(": ").append(f.reason());
                                }
                                if (data.failures().size() > 5) {
                                    msg.append("\n...等共 ").append(data.failures().size()).append(" 条失败");
                                }
                            }
                            JOptionPane.showMessageDialog(parent, msg.toString());
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

    static List<ImportMajorTransferScoresCommand.ScoreEntry> parse(File file)
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
            String headerLine = reader.readLine();
            if (headerLine == null) return entries;
            String[] headers = splitCsv(headerLine);
            ColumnMapping mapping = ColumnMapping.resolve(headers);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = splitCsv(line);
                if (cells.length == 0) continue;
                String id = cell(cells, mapping.idCol());
                if (id.isBlank()) continue;
                entries.add(new ImportMajorTransferScoresCommand.ScoreEntry(id,
                        decimal(cells, mapping.writtenCol()), decimal(cells, mapping.interviewCol())));
            }
        }
        return entries;
    }

    private static List<ImportMajorTransferScoresCommand.ScoreEntry> excel(File file)
            throws Exception {
        List<ImportMajorTransferScoresCommand.ScoreEntry> entries = new ArrayList<>();
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(file)) {
            var sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) return entries;
            var headerRow = sheet.getRow(0);
            String[] headers = new String[headerRow != null ? headerRow.getLastCellNum() : 0];
            for (int i = 0; i < headers.length; i++) headers[i] = text(headerRow.getCell(i));
            ColumnMapping mapping = ColumnMapping.resolve(headers);
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null) continue;
                String id = text(row.getCell(mapping.idCol()));
                if (!id.isBlank()) {
                    entries.add(new ImportMajorTransferScoresCommand.ScoreEntry(id,
                            mapping.writtenCol() >= 0 ? number(row.getCell(mapping.writtenCol())) : null,
                            mapping.interviewCol() >= 0 ? number(row.getCell(mapping.interviewCol())) : null));
                }
            }
        }
        return entries;
    }

    private static String[] splitCsv(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    private static String cell(String[] cells, int index) {
        if (index < 0 || index >= cells.length) return "";
        String val = cells[index].trim();
        if (val.startsWith("\uFEFF")) val = val.substring(1).trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1).replace("\"\"", "\"").trim();
        }
        return val;
    }

    private static BigDecimal decimal(String[] cells, int index) {
        String value = cell(cells, index);
        return value.isBlank() ? null : new BigDecimal(value);
    }

    private static String text(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        String val = new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell).trim();
        return val.startsWith("\uFEFF") ? val.substring(1).trim() : val;
    }

    private static BigDecimal number(org.apache.poi.ss.usermodel.Cell cell) {
        String value = text(cell);
        return value.isBlank() ? null : new BigDecimal(value);
    }

    private record ColumnMapping(int idCol, int writtenCol, int interviewCol) {
        static ColumnMapping resolve(String[] headers) {
            int id = -1, written = -1, interview = -1;
            for (int i = 0; i < headers.length; i++) {
                String h = cell(headers, i);
                if (h.contains("申请编号") || h.contains("申请ID") || h.equalsIgnoreCase("applicationId")) {
                    id = i;
                } else if (id == -1 && (h.contains("一卡通号") || h.contains("学号") || h.equalsIgnoreCase("studentId"))) {
                    id = i;
                } else if (h.contains("笔试") || h.equalsIgnoreCase("writtenScore")) {
                    written = i;
                } else if (h.contains("面试") || h.equalsIgnoreCase("interviewScore")) {
                    interview = i;
                }
            }
            if (id == -1) id = 0;
            if (written == -1) written = headers.length > 1 ? 1 : -1;
            if (interview == -1) interview = headers.length > 2 ? 2 : -1;
            return new ColumnMapping(id, written, interview);
        }
    }
}
