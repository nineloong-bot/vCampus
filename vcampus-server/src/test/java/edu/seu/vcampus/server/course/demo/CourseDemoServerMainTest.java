package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.common.course.CourseSelectionQuery;
import edu.seu.vcampus.common.course.AcademicSeason;
import edu.seu.vcampus.server.course.domain.CurriculumCourseUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseDemoServerMainTest {
    @TempDir Path directory;

    @Test
    void preparesRealAccessDataAndCompletesAnEnrollment() throws Exception {
        var runtime = CourseDemoServerMain.prepare(directory.resolve("course-demo.accdb"), schema(), "ENROLLMENT");
        var term = runtime.service().listTerms().getFirst();
        var offerings = runtime.service().searchOfferings(
                new OfferingSearchQuery(term.termId(), "", null, true, 0, 20)).items();

        var selectable = offerings.stream().filter(offering -> "B09G0011".equals(offering.courseCode()))
                .findFirst().orElseThrow();
        var enrollment = runtime.service().enroll("student-demo-1", new EnrollCommand(selectable.offeringId()));

        assertThat(term.academicYearStart()).isEqualTo(2026);
        assertThat(term.season()).isEqualTo(AcademicSeason.AUTUMN);
        assertThat(offerings).extracting(offering -> offering.courseCode())
                .contains("B09D0012", "B09G0011", "B09N0014", "B71S0032", "BJSL0061", "B09S0061")
                .doesNotContain("MATH101", "CS201", "DEMO-RACE");
        assertThat(offerings).filteredOn(offering -> "B09D0012".equals(offering.courseCode()))
                .singleElement().satisfies(offering -> {
                    assertThat(offering.courseName()).isEqualTo("数据库原理");
                    assertThat(offering.capacity()).isEqualTo(43);
                    assertThat(offering.retakeCapacity()).isEqualTo(8);
                });
        var student1 = runtime.service().searchStudentCourses("student-demo-1",
                new CourseSelectionQuery(term.termId(), "", null, 0, 100));
        var student2 = runtime.service().searchStudentCourses("student-demo-2",
                new CourseSelectionQuery(term.termId(), "", null, 0, 100));
        assertThat(student1.items()).extracting(row -> row.courseCode()).doesNotContain("BJSL0061");
        assertThat(student1.items()).extracting(row -> row.courseCode()).doesNotContain("B09S0061");
        var futureOffering = offerings.stream().filter(offering -> "B09S0061".equals(offering.courseCode()))
                .findFirst().orElseThrow();
        assertThatThrownBy(() -> runtime.service().enroll("student-demo-1",
                new EnrollCommand(futureOffering.offeringId())))
                .isInstanceOf(CurriculumCourseUnavailableException.class);
        assertThat(student2.items()).filteredOn(row -> "BJSL0061".equals(row.courseCode()))
                .singleElement().satisfies(row -> assertThat(row.retakeCourse()).isTrue());
        assertThat(enrollment.studentId()).isEqualTo("student-demo-1");
        assertThat(runtime.service().getCurrentSchedule("student-demo-1")).hasSize(1);
        CourseDemoDataset.install(runtime.connections(), runtime.service(), term);
        assertThat(runtime.service().searchCatalog(
                new edu.seu.vcampus.common.course.CourseCatalogQuery("B09D0012", true, 0, 10)).total())
                .isEqualTo(1);
        assertThat(Files.isRegularFile(directory.resolve("course-demo.accdb"))).isTrue();
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
