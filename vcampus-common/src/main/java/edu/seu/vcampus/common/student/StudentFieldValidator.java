package edu.seu.vcampus.common.student;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared semantic validation used by student editors and authoritative server writes. */
public final class StudentFieldValidator {
    private static final Pattern CAMPUS_CARD = Pattern.compile("2[123]3\\d{6}");
    private static final Pattern STUDENT_NUMBER = Pattern.compile("[0-9A-Z]{3}\\d{5}");
    private static final Pattern PASSPORT = Pattern.compile("[A-Z0-9]{5,18}");
    private static final Pattern HK_MACAU_TAIWAN = Pattern.compile("[A-Z0-9()]{6,20}");
    private static final Pattern OTHER_DOCUMENT = Pattern.compile("[A-Z0-9-]{4,32}");
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern LANDLINE = Pattern.compile("0\\d{2,3}-?\\d{7,8}");
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private StudentFieldValidator() { }

    public static List<StudentFieldError> validateManual(CreateStudentManualCommand raw, LocalDate today) {
        ArrayList<StudentFieldError> errors = new ArrayList<>();
        if (raw == null) {
            errors.add(new StudentFieldError("student", "新增学生信息不能为空"));
            return List.copyOf(errors);
        }
        CreateStudentManualCommand value = normalizeManual(raw);
        requirePattern(errors, "campusCardNumber", value.campusCardNumber(), CAMPUS_CARD,
                "一卡通号必须为 9 位数字，格式如 213240001");
        if (value.studentType() != null && value.campusCardNumber() != null
                && CAMPUS_CARD.matcher(value.campusCardNumber()).matches()
                && value.campusCardNumber().charAt(1) != value.studentType().digit()) {
            errors.add(new StudentFieldError("campusCardNumber", "一卡通号的学生类别位与学生类型不一致"));
        }
        requirePattern(errors, "studentNumber", value.studentNumber(), STUDENT_NUMBER,
                "学号必须为 3 位大写字母/数字加 5 位数字");
        if (blank(value.studentName()) || value.studentName().length() < 2 || value.studentName().length() > 64) {
            errors.add(new StudentFieldError("studentName", "姓名长度必须为 2–64 个字符"));
        }
        if (!"男".equals(value.gender()) && !"女".equals(value.gender())) {
            errors.add(new StudentFieldError("gender", "性别必须选择男或女"));
        }
        if (value.studentType() == null) errors.add(new StudentFieldError("studentType", "请选择学生类型"));
        validateDocument(errors, value.idDocumentType(), value.idDocumentNumber(), value.birthDate(), true);
        validateNotFuture(errors, "birthDate", value.birthDate(), today, "出生日期");
        validateNotFuture(errors, "enrollmentDate", value.enrollmentDate(), today, "入学日期");
        if (value.birthDate() != null && value.enrollmentDate() != null
                && value.enrollmentDate().isBefore(value.birthDate().plusYears(18))) {
            errors.add(new StudentFieldError("enrollmentDate", "入学时必须已年满 18 周岁"));
        }
        if (blank(value.classId())) errors.add(new StudentFieldError("classId", "请选择所属班级"));
        return List.copyOf(errors);
    }

    public static List<StudentFieldError> validatePersonal(StudentPersonalProfile value, LocalDate today) {
        ArrayList<StudentFieldError> errors = new ArrayList<>();
        if (value == null) {
            errors.add(new StudentFieldError("personal", "个人信息不能为空"));
            return List.copyOf(errors);
        }
        validateDocument(errors, value.idDocumentType(), value.idDocumentNumber(), value.birthDate(), false);
        validateNotFuture(errors, "idIssuedDate", value.idIssuedDate(), today, "证件签发日期");
        validateNotFuture(errors, "birthDate", value.birthDate(), today, "出生日期");
        validateNotFuture(errors, "leagueJoinDate", value.leagueJoinDate(), today, "入团日期");
        validateNotFuture(errors, "partyJoinDate", value.partyJoinDate(), today, "入党日期");
        if (value.leagueMember() && value.leagueJoinDate() == null) {
            errors.add(new StudentFieldError("leagueJoinDate", "团员必须填写入团日期"));
        }
        if (!value.leagueMember() && value.leagueJoinDate() != null) {
            errors.add(new StudentFieldError("leagueJoinDate", "非团员不应填写入团日期"));
        }
        if (value.partyMember() && value.partyJoinDate() == null) {
            errors.add(new StudentFieldError("partyJoinDate", "党员必须填写入党日期"));
        }
        if (!value.partyMember() && value.partyJoinDate() != null) {
            errors.add(new StudentFieldError("partyJoinDate", "非党员不应填写入党日期"));
        }
        range(errors, "weightKg", value.weightKg(), 20, 300, "体重必须在 20–300 KG 之间");
        range(errors, "heightCm", value.heightCm(), 100, 280, "身高必须在 100–280 CM 之间");
        if (!blank(value.email()) && (value.email().length() > 128 || !EMAIL.matcher(value.email()).matches())) {
            errors.add(new StudentFieldError("email", "邮箱格式不正确，例如 student@seu.edu.cn"));
        }
        if (!blank(value.phone()) && !MOBILE.matcher(value.phone()).matches() && !LANDLINE.matcher(value.phone()).matches()) {
            errors.add(new StudentFieldError("phone", "请填写 11 位大陆手机号或固定电话"));
        }
        return List.copyOf(errors);
    }

    public static CreateStudentManualCommand normalizeManual(CreateStudentManualCommand value) {
        if (value == null) return null;
        return new CreateStudentManualCommand(
                trim(value.campusCardNumber()), upper(value.studentNumber()), trim(value.studentName()),
                trim(value.gender()), value.studentType(), trim(value.idDocumentType()),
                upper(value.idDocumentNumber()), value.birthDate(), value.enrollmentDate(), trim(value.classId()));
    }

    private static void validateDocument(List<StudentFieldError> errors, String rawType, String rawNumber,
                                         LocalDate birthDate, boolean required) {
        String type = trim(rawType);
        String number = upper(rawNumber);
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
        else if ("港澳台居民居住证".equals(type) || "港澳台证件".equals(type)) valid = HK_MACAU_TAIWAN.matcher(number).matches();
        else valid = OTHER_DOCUMENT.matcher(number).matches();
        if (!valid) errors.add(new StudentFieldError("idDocumentNumber", "证件号码格式不正确或与出生日期不一致"));
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

    private static void requirePattern(List<StudentFieldError> errors, String field, String value,
                                       Pattern pattern, String message) {
        if (blank(value) || !pattern.matcher(value).matches()) errors.add(new StudentFieldError(field, message));
    }

    private static void validateNotFuture(List<StudentFieldError> errors, String field, LocalDate value,
                                          LocalDate today, String label) {
        if (value != null && value.isAfter(today)) errors.add(new StudentFieldError(field, label + "不能晚于今天"));
    }

    private static void range(List<StudentFieldError> errors, String field, Integer value,
                              int min, int max, String message) {
        if (value != null && (value < min || value > max)) errors.add(new StudentFieldError(field, message));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
}
