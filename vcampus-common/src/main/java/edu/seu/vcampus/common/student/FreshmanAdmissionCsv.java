package edu.seu.vcampus.common.student;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Parses and validates the fixed five-column freshman admission CSV format. */
public final class FreshmanAdmissionCsv {
    private static final List<String> HEADER = List.of("姓名", "性别", "身份证", "学院", "专业");
    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final String CHECK_CODES = "10X98765432";

    private FreshmanAdmissionCsv() { }

    /** Parses all input rows and returns every format and field error. */
    public static FreshmanAdmissionCsvResult parse(String csv) {
        List<FreshmanAdmissionValidationError> errors = new ArrayList<>();
        List<List<String>> records = records(csv, errors);
        if (records.isEmpty()) return new FreshmanAdmissionCsvResult(List.of(), errors);
        if (!HEADER.equals(records.getFirst())) {
            errors.add(error(1, "CSV", "表头必须为：姓名,性别,身份证,学院,专业"));
            return new FreshmanAdmissionCsvResult(List.of(), errors);
        }
        List<FreshmanAdmissionRow> rows = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 1; index < records.size(); index++) validate(records.get(index), index + 1, ids, rows, errors);
        return new FreshmanAdmissionCsvResult(rows, errors);
    }

    private static List<List<String>> records(String csv, List<FreshmanAdmissionValidationError> errors) {
        if (csv == null || csv.isBlank()) {
            errors.add(error(1, "CSV", "CSV 文件不能为空"));
            return List.of();
        }
        String input = csv.startsWith("\uFEFF") ? csv.substring(1) : csv;
        List<List<String>> result = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < input.length(); i++) {
            char value = input.charAt(i);
            if (value == '"') {
                if (quoted && i + 1 < input.length() && input.charAt(i + 1) == '"') { field.append(value); i++; }
                else quoted = !quoted;
            } else if (value == ',' && !quoted) { row.add(field.toString().trim()); field.setLength(0); }
            else if ((value == '\n' || value == '\r') && !quoted) {
                if (value == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                row.add(field.toString().trim()); field.setLength(0); result.add(List.copyOf(row)); row.clear();
            } else field.append(value);
        }
        if (quoted) errors.add(error(result.size() + 1, "CSV", "CSV 引号未闭合"));
        if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString().trim()); result.add(List.copyOf(row)); }
        return result;
    }

    private static void validate(List<String> values, int line, Set<String> ids,
                                 List<FreshmanAdmissionRow> rows,
                                 List<FreshmanAdmissionValidationError> errors) {
        if (values.size() != HEADER.size()) { errors.add(error(line, "CSV", "每行必须包含 5 列")); return; }
        boolean valid = true;
        for (int index = 0; index < values.size(); index++) {
            if (values.get(index).isBlank()) { errors.add(error(line, HEADER.get(index), "不能为空")); valid = false; }
        }
        String gender = values.get(1);
        if (!gender.equals("男") && !gender.equals("女")) { errors.add(error(line, "性别", "只能填写男或女")); valid = false; }
        String id = values.get(2).toUpperCase(Locale.ROOT);
        if (!validResidentId(id)) { errors.add(error(line, "身份证", "格式或校验码不正确")); valid = false; }
        else if (!ids.add(id)) { errors.add(error(line, "身份证", "在 CSV 中重复")); valid = false; }
        if (valid) rows.add(new FreshmanAdmissionRow(line, values.get(0), gender, id, values.get(3), values.get(4)));
    }

    private static boolean validResidentId(String value) {
        if (!value.matches("[1-9]\\d{16}[0-9X]")) return false;
        int sum = 0;
        for (int index = 0; index < WEIGHTS.length; index++) sum += (value.charAt(index) - '0') * WEIGHTS[index];
        return CHECK_CODES.charAt(sum % 11) == value.charAt(17);
    }

    private static FreshmanAdmissionValidationError error(int line, String field, String message) {
        return new FreshmanAdmissionValidationError(line, field, message);
    }
}
