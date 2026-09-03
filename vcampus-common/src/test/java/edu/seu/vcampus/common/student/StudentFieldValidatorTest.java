package edu.seu.vcampus.common.student;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StudentFieldValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    @Test
    void acceptsAValidManualStudentAndNormalizesStudentNumber() {
        var command = new CreateStudentManualCommand(
                "213240001", "abc24001", "张三", "男", StudentType.UNDERGRADUATE,
                "居民身份证", "110105200609030015", LocalDate.of(2006, 9, 3),
                LocalDate.of(2024, 9, 3), "class-1");

        assertThat(StudentFieldValidator.validateManual(command, TODAY)).isEmpty();
        assertThat(StudentFieldValidator.normalizeManual(command).studentNumber()).isEqualTo("ABC24001");
    }

    @Test
    void rejectsManualStudentWhoWasNotEighteenAtEnrollment() {
        var command = new CreateStudentManualCommand(
                "213240001", "ABC24001", "张三", "男", StudentType.UNDERGRADUATE,
                "居民身份证", "110105200609040010", LocalDate.of(2006, 9, 4),
                LocalDate.of(2024, 9, 3), "class-1");

        assertThat(StudentFieldValidator.validateManual(command, TODAY))
                .extracting(StudentFieldError::field)
                .contains("enrollmentDate");
    }

    @Test
    void rejectsMalformedNumbersAndMismatchedResidentIdBirthDate() {
        var command = new CreateStudentManualCommand(
                "123", "bad-number", "A", "未知", StudentType.UNDERGRADUATE,
                "居民身份证", "11010519491231002X", LocalDate.of(2000, 1, 1),
                LocalDate.of(2020, 9, 1), "");

        assertThat(StudentFieldValidator.validateManual(command, TODAY))
                .extracting(StudentFieldError::field)
                .contains("campusCardNumber", "studentNumber", "studentName", "gender",
                        "idDocumentNumber", "classId");
    }

    @Test
    void validatesPersonalRangesContactsAndMembershipDates() {
        var personal = personal(
                "bad-email", "12345", 19, 281,
                true, null, false, LocalDate.of(2020, 1, 1));

        assertThat(StudentFieldValidator.validatePersonal(personal, TODAY))
                .extracting(StudentFieldError::field)
                .contains("email", "phone", "weightKg", "heightCm", "leagueJoinDate", "partyJoinDate");
    }

    @Test
    void requiresDocumentTypeAndNumberTogetherAndRejectsFutureDates() {
        var personal = new StudentPersonalProfile(
                null, null, null, null, null, "护照", null,
                TODAY.plusDays(1), TODAY.plusDays(1), null, null, null, null,
                null, null, null, null, null, false, null, false, null,
                null, null, null, null, null, null, false, null, null);

        assertThat(StudentFieldValidator.validatePersonal(personal, TODAY))
                .extracting(StudentFieldError::field)
                .contains("idDocumentNumber", "idIssuedDate", "birthDate");
    }

    private static StudentPersonalProfile personal(
            String email, String phone, Integer weight, Integer height,
            boolean leagueMember, LocalDate leagueDate,
            boolean partyMember, LocalDate partyDate) {
        return new StudentPersonalProfile(
                "ZHANG SAN", null, "共青团员", "汉族", "未婚",
                "居民身份证", "11010519491231002X", null,
                LocalDate.of(1949, 12, 31), "北京市", "中国", "北京市", "北京市",
                "非农业家庭户口", null, null, "否", "无宗教信仰",
                leagueMember, leagueDate, partyMember, partyDate, "健康或良好", "A",
                weight, height, null, null, false, email, phone);
    }
}
