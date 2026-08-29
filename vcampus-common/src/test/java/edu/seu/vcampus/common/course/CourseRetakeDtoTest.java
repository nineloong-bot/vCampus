package edu.seu.vcampus.common.course;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseRetakeDtoTest {
    @Test
    void outcomeContractContainsOnlyPassedAndFailed() {
        assertThat(CourseOutcome.values()).containsExactly(CourseOutcome.PASSED, CourseOutcome.FAILED);
    }

    @Test
    void importAndRetakeMessagesRoundTripWithoutGradeOrScoreProperties() throws Exception {
        ImportCourseOutcomesCommand command = new ImportCourseOutcomesCommand(List.of(
                new ImportCourseOutcomesCommand.OutcomeEntry(
                        "student-1", "course-1", "term-1", CourseOutcome.FAILED, "source-1")));
        RetakeEligibility eligibility = new RetakeEligibility(
                "course-1", true, List.of("attempt-1"), null);

        assertThat(roundTrip(command)).isEqualTo(command);
        assertThat(roundTrip(new RetakeCommand("offering-1")))
                .isEqualTo(new RetakeCommand("offering-1"));
        assertThat(roundTrip(eligibility)).isEqualTo(eligibility);
        assertThat(componentNames(ImportCourseOutcomesCommand.OutcomeEntry.class))
                .containsExactly("studentId", "courseId", "termId", "outcome", "sourceReference")
                .noneMatch(name -> name.toLowerCase().contains("grade")
                        || name.toLowerCase().contains("score"));
    }

    @Test
    void messageBoundaryRejectsNullBlankEmptyAndOversizedImportData() {
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand.OutcomeEntry(
                " ", "course-1", "term-1", CourseOutcome.FAILED, "source-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand.OutcomeEntry(
                "student-1", "course-1", "term-1", null, "source-1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand.OutcomeEntry(
                "student-1", "course-1", "term-1", CourseOutcome.FAILED, "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand.OutcomeEntry(
                "s".repeat(37), "course-1", "term-1", CourseOutcome.FAILED, "source-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand.OutcomeEntry(
                "student-1", "c".repeat(37), "term-1", CourseOutcome.FAILED, "source-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImportCourseOutcomesCommand.OutcomeEntry(
                "student-1", "course-1", "t".repeat(37), CourseOutcome.FAILED, "source-1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetakeCommand(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }
}
