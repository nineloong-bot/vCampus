package edu.seu.vcampus.common.student;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

/** Validates identity-document fields used by student profiles. */
final class StudentDocumentValidator {
    private static final Pattern PASSPORT = Pattern.compile("[A-Z0-9]{5,18}");
    private static final Pattern HK_MACAU_TAIWAN = Pattern.compile("[A-Z0-9()]{6,20}");
    private static final Pattern OTHER_DOCUMENT = Pattern.compile("[A-Z0-9-]{4,32}");
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private StudentDocumentValidator() {
    }

    static void validate(List<StudentFieldError> errors, String rawType, String rawNumber,
                         LocalDate birthDate, boolean required) {
        String type = normalize(rawType);
        String number = rawNumber == null ? null : rawNumber.trim().toUpperCase(java.util.Locale.ROOT);
        if (blank(type) && blank(number)) {
            if (required) {
                errors.add(new StudentFieldError("idDocumentType", "请选择证件类型"));
                errors.add(new StudentFieldError("idDocumentNumber", "请填写证件号码"));
            }
            return;
        }
        if (blank(type)) {
            errors.add(new StudentFieldError("idDocumentType", "填写证件号码时必须选择证件类型"));
            return;
        }
        if (blank(number)) {
            errors.add(new StudentFieldError("idDocumentNumber", "选择证件类型后必须填写证件号码"));
            return;
        }
        boolean valid;
        if ("居民身份证".equals(type)) valid = validResidentId(number, birthDate);
        else if ("护照".equals(type)) valid = PASSPORT.matcher(number).matches();
        else if ("港澳台居民居住证".equals(type) || "港澳台证件".equals(type)) {
            valid = HK_MACAU_TAIWAN.matcher(number).matches();
        } else valid = OTHER_DOCUMENT.matcher(number).matches();
        if (!valid) errors.add(new StudentFieldError(
                "idDocumentNumber", "证件号码格式不正确或与出生日期不一致"));
    }

    private static boolean validResidentId(String value, LocalDate birthDate) {
        if (!value.matches("\\d{17}[0-9X]")) return false;
        LocalDate embedded;
        try {
            embedded = LocalDate.of(Integer.parseInt(value.substring(6, 10)),
                    Integer.parseInt(value.substring(10, 12)), Integer.parseInt(value.substring(12, 14)));
        } catch (DateTimeException | NumberFormatException ignored) {
            return false;
        }
        if (birthDate != null && !birthDate.equals(embedded)) return false;
        int sum = 0;
        for (int i = 0; i < ID_WEIGHTS.length; i++) sum += (value.charAt(i) - '0') * ID_WEIGHTS[i];
        return value.charAt(17) == ID_CHECK_CODES[sum % 11];
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
