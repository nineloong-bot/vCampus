package edu.seu.vcampus.common.course;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseSelectionDtoTest {
    @Test
    void phaseCommandsValidateAndRoundTrip() throws Exception {
        CreateSelectionPhaseCommand create = new CreateSelectionPhaseCommand(
                "term-1", "ENROLLMENT", "2026-2027秋季学期选课");

        assertThat(roundTrip(create)).isEqualTo(create);
        assertThatThrownBy(() -> new CreateSelectionPhaseCommand("term-1", "AUTO", "选课"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangeSelectionPhaseStatusCommand("p1", "DRAFT", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void groupedCourseDefensivelyCopiesTeachingClasses() {
        OfferingSummary offering = offering();
        List<TeachingClassOptionView> mutable = new java.util.ArrayList<>(List.of(
                new TeachingClassOptionView(offering, "ENROLL", null)));

        CourseSelectionView course = new CourseSelectionView(
                "c1", "MATH101", "高等数学", "SELECT_COURSE", null,
                null, null, null, mutable);
        mutable.clear();

        assertThat(course.teachingClasses()).hasSize(1);
        assertThatThrownBy(() -> course.teachingClasses().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void activeEnrollmentFieldsAreAllPresentOrAllAbsent() {
        assertThatThrownBy(() -> new CourseSelectionView(
                "c1", "MATH101", "高等数学", "CANCEL_SELECTION", null,
                "e1", null, "o1", List.of(new TeachingClassOptionView(
                        offering(), "SELECTED", null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryRejectsInvalidPagingAndWeekday() {
        assertThatThrownBy(() -> new CourseSelectionQuery("term-1", "", "FUNDAY", 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseSelectionQuery("term-1", "", null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseSelectionQuery("term-1", "", null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contextRequiresAllPhaseFieldsTogether() {
        assertThatThrownBy(() -> new StudentSelectionContextView(
                "term-1", "秋季学期", "ACTIVE", "p1", null, "秋季选课",
                Instant.parse("2026-09-04T00:00:00Z"), true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Object roundTrip(Object value) throws Exception {
        byte[] bytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ObjectOutputStream output = new ObjectOutputStream(buffer)) {
            output.writeObject(value);
            bytes = buffer.toByteArray();
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return input.readObject();
        }
    }

    private static OfferingSummary offering() {
        return new OfferingSummary(
                "o1", "term-1", "c1", "MATH101", "高等数学", "t1", "01班",
                40, 10, "OPEN", 0, List.of());
    }
}
