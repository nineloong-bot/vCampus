package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.server.course.repository.CurriculumCatalogCandidateRepository.Definition;
import edu.seu.vcampus.common.course.CourseDepartmentOption;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class CurriculumCatalogCandidateServiceTest {
    @Test void fillsLegacyHoursAndOwnerFromAnExistingCatalogCourse() {
        var plan = new Definition("pc-1", "EN101", "旧课程名称", new BigDecimal("2"),
                null, "REQUIRED", "legacy-department", "旧学院");
        var catalog = new edu.seu.vcampus.server.course.repository.Course(
                "course-1", "EN101", "大学英语", "language", "外国语学院",
                new BigDecimal("3"), 48, "", true, 0, null, null);

        var result = CurriculumCatalogCandidateService.mergeWithCatalog(
                List.of(plan), List.of(catalog));

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.totalHours()).isEqualTo(48);
            assertThat(row.courseName()).isEqualTo("大学英语");
            assertThat(row.credits()).isEqualByComparingTo("3");
            assertThat(row.departmentId()).isEqualTo("language");
            assertThat(row.departmentName()).isEqualTo("外国语学院");
        });
    }

    @Test void deduplicatesIdenticalDefinitionsAndMarksMetadataConflicts() {
        var rows = List.of(
                definition("pc-1", "CS101", "程序设计", "3", 48, "REQUIRED", "d1"),
                definition("pc-2", "cs101", "程序设计", "3.0", 48, "REQUIRED", "d1"),
                definition("pc-3", "CS102", "数据结构", "3", 48, "REQUIRED", "d1"),
                definition("pc-4", "CS102", "数据结构", "4", 64, "REQUIRED", "d1"),
                definition("pc-5", "CS103", "数据库", "3", 48, "REQUIRED", "d1"));

        var result = CurriculumCatalogCandidateService.aggregate(rows);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).courseCode()).isEqualTo("CS101");
        assertThat(result.get(0).conflicted()).isFalse();
        assertThat(result.get(1).courseCode()).isEqualTo("CS102");
        assertThat(result.get(1).conflicted()).isTrue();
        assertThat(result.get(2).courseCode()).isEqualTo("CS103");
        assertThat(result.get(2).conflicted()).isFalse();
    }

    @Test void derivesUniqueCollegeOptionsFromCanonicalDefinitions() {
        var rows = List.of(
                definition("pc-1", "CS101", "程序设计", "3", 48, "REQUIRED", "dept-cse"),
                definition("pc-2", "CS102", "数据结构", "3", 48, "REQUIRED", "dept-cse"),
                new Definition("pc-3", "MA101", "高等数学", new BigDecimal("5"), 80,
                        "REQUIRED", "dept-math", "数学学院"));

        assertThat(CurriculumCatalogCandidateService.departmentOptions(rows))
                .extracting(CourseDepartmentOption::departmentId, CourseDepartmentOption::departmentName)
                .containsExactly(tuple("dept-cse", "计算机学院"), tuple("dept-math", "数学学院"));
    }

    private static Definition definition(String id, String code, String name, String credits,
                                         int hours, String nature, String department) {
        return new Definition(id, code, name, new BigDecimal(credits), hours, nature,
                department, "计算机学院");
    }
}
