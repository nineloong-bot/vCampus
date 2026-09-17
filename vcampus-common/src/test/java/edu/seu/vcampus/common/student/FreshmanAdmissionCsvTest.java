package edu.seu.vcampus.common.student;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests fixed-format freshman admission CSV validation. */
class FreshmanAdmissionCsvTest {
    @Test
    void acceptsFixedHeaderAndNormalizesBomAndWhitespace() {
        var result = FreshmanAdmissionCsv.parse("\uFEFF姓名,性别,身份证,学院,专业\n"
                + " 张三 , 男 ,11010519491231002X,计算机科学与工程学院,软件工程\n");

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).containsExactly(new FreshmanAdmissionRow(
                2, "张三", "男", "11010519491231002X", "计算机科学与工程学院", "软件工程"));
    }

    @Test
    void reportsFieldErrorsForInvalidRowsWithoutDiscardingOtherErrors() {
        var result = FreshmanAdmissionCsv.parse("姓名,性别,身份证,学院,专业\n"
                + "张三,未知,11010519491231002X,计算机科学与工程学院,软件工程\n"
                + "李四,女,11010519491231002X,数学学院,数学与应用数学\n"
                + "王五,男,,计算机科学与工程学院\n");

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).extracting(FreshmanAdmissionValidationError::lineNumber,
                        FreshmanAdmissionValidationError::field)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(2, "性别"),
                        org.assertj.core.groups.Tuple.tuple(3, "身份证"),
                        org.assertj.core.groups.Tuple.tuple(4, "CSV"));
    }

    @Test
    void rejectsResidentIdWithChecksumButImpossibleBirthDate() {
        var result = FreshmanAdmissionCsv.parse("姓名,性别,身份证,学院,专业\n"
                + "张三,男,110105200001000010,计算机科学与工程学院,软件工程\n");

        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).extracting(FreshmanAdmissionValidationError::field)
                .containsExactly("身份证");
    }
}
