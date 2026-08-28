package edu.seu.vcampus.server.course.demo;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.OfferingSearchQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CourseDemoServerMainTest {
    @TempDir Path directory;

    @Test
    void preparesRealAccessDataAndCompletesAnEnrollment() throws Exception {
        var runtime = CourseDemoServerMain.prepare(directory.resolve("course-demo.accdb"), schema(), "ENROLLMENT");
        var term = runtime.service().listTerms().getFirst();
        var offerings = runtime.service().searchOfferings(
                new OfferingSearchQuery(term.termId(), "", null, true, 0, 20)).items();

        var enrollment = runtime.service().enroll("student-demo-1", new EnrollCommand(offerings.getFirst().offeringId()));

        assertThat(offerings).hasSize(2);
        assertThat(enrollment.studentId()).isEqualTo("student-demo-1");
        assertThat(runtime.service().getCurrentSchedule("student-demo-1")).hasSize(1);
        assertThat(Files.isRegularFile(directory.resolve("course-demo.accdb"))).isTrue();
    }

    private static Path schema() {
        Path direct = Path.of("vcampus-database", "schema", "030_course.sql");
        return Files.exists(direct) ? direct : Path.of("..", "vcampus-database", "schema", "030_course.sql");
    }
}
