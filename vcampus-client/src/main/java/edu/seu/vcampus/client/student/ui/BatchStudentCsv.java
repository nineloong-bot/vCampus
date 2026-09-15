package edu.seu.vcampus.client.student.ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Parses the four-column student allocation CSV format. */
final class BatchStudentCsv {
    private BatchStudentCsv() { }

    static List<BatchAssignmentTableModel.Row> parse(File file) throws IOException {
        List<BatchAssignmentTableModel.Row> rows = new ArrayList<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) throw new IllegalArgumentException("文件为空");
            if (header.split(",", -1).length < 4) {
                throw new IllegalArgumentException("至少需要4列: 姓名,一卡通号,性别,综合成绩");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                String[] values = line.split(",", -1);
                if (values.length < 4) throw invalid(lineNumber, "列数不足");
                String name = values[0].trim();
                String card = values[1].trim();
                String gender = values[2].trim();
                if (name.isEmpty()) throw invalid(lineNumber, "姓名为空");
                if (card.isEmpty()) throw invalid(lineNumber, "一卡通号为空");
                if (!"男".equals(gender) && !"女".equals(gender)) {
                    throw invalid(lineNumber, "性别必须为'男'或'女'");
                }
                try {
                    rows.add(new BatchAssignmentTableModel.Row(name, card, gender,
                            Double.parseDouble(values[3].trim()), 0));
                } catch (NumberFormatException failure) {
                    throw invalid(lineNumber, "成绩格式错误");
                }
            }
        }
        return rows;
    }

    private static IllegalArgumentException invalid(int line, String message) {
        return new IllegalArgumentException("第" + line + "行: " + message);
    }
}
