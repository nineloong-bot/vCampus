package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import edu.seu.vcampus.server.course.demo.CourseDemoServerMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CourseQueryPortTest {
    @TempDir Path directory;

    @Test
    void publishesOnlyCurrentActiveCourseSummariesToOtherModules() throws Exception {
        var runtime = CourseDemoServerMain.prepare(directory.resolve("query.accdb"), schema(), "ENROLLMENT");
        var term = runtime.service().listTerms().getFirst();
        var offering = runtime.service().searchOfferings(
                new OfferingSearchQuery(term.termId(), "MATH101", null, true, 0, 20)).items().getFirst();
        runtime.service().enroll("student-demo-1", new EnrollCommand(offering.offeringId()));
        CourseQueryPort query = (CourseQueryPort) runtime.service();

        assertThat(query.hasActiveEnrollment("student-demo-1")).isTrue();
        assertThat(query.hasActiveEnrollment("student-demo-2")).isFalse();
        assertThat(query.findCoursesByStudent("student-demo-1"))
                .singleElement().satisfies(course -> {
                    assertThat(course.courseCode()).isEqualTo("MATH101");
                    assertThat(course.courseName()).isEqualTo("高等数学");
                });
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
