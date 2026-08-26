package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.Term;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class TermWindowPolicyTest {
    private static final Instant ENROLLMENT_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant ENROLLMENT_END = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant ADJUSTMENT_START = Instant.parse("2026-08-11T00:00:00Z");
    private static final Instant ADJUSTMENT_END = Instant.parse("2026-08-20T00:00:00Z");

    private final TermWindowPolicy windows = new TermWindowPolicy();

    @ParameterizedTest(name = "{index}: enrollment at {1} is {2}")
    @MethodSource("enrollmentWindowCases")
    void enrollmentWindowIncludesStartAndExcludesEnd(Instant now, boolean open, String description) {
        if (open) {
            assertThatCode(() -> windows.requireEnrollmentOpen(activeTerm(), now))
                    .as(description)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> windows.requireEnrollmentOpen(activeTerm(), now))
                    .as(description)
                    .isInstanceOf(EnrollmentClosedException.class)
                    .hasMessageContaining("COURSE_ENROLLMENT_NOT_OPEN");
        }
    }

    static Stream<Arguments> enrollmentWindowCases() {
        return Stream.of(
                arguments(ENROLLMENT_START.minusNanos(1), false, "just before enrollment start"),
                arguments(ENROLLMENT_START, true, "at enrollment start"),
                arguments(ENROLLMENT_START.plusSeconds(1), true, "inside enrollment window"),
                arguments(ENROLLMENT_END.minusNanos(1), true, "just before enrollment end"),
                arguments(ENROLLMENT_END, false, "at enrollment end"),
                arguments(ENROLLMENT_END.plusNanos(1), false, "just after enrollment end"));
    }

    @ParameterizedTest(name = "{index}: adjustment at {1} is {2}")
    @MethodSource("adjustmentWindowCases")
    void adjustmentWindowIncludesStartAndExcludesEnd(Instant now, boolean open, String description) {
        if (open) {
            assertThatCode(() -> windows.requireAdjustmentOpen(activeTerm(), now))
                    .as(description)
                    .doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> windows.requireAdjustmentOpen(activeTerm(), now))
                    .as(description)
                    .isInstanceOf(AdjustmentClosedException.class)
                    .hasMessageContaining("COURSE_ADJUSTMENT_NOT_OPEN");
        }
    }

    static Stream<Arguments> adjustmentWindowCases() {
        return Stream.of(
                arguments(ADJUSTMENT_START.minusNanos(1), false, "just before adjustment start"),
                arguments(ADJUSTMENT_START, true, "at adjustment start"),
                arguments(ADJUSTMENT_START.plusSeconds(1), true, "inside adjustment window"),
                arguments(ADJUSTMENT_END.minusNanos(1), true, "just before adjustment end"),
                arguments(ADJUSTMENT_END, false, "at adjustment end"),
                arguments(ADJUSTMENT_END.plusNanos(1), false, "just after adjustment end"));
    }

    @Test
    void closedTermRejectsMutationsEvenInsideConfiguredWindows() {
        Term term = termWithStatus("CLOSED");

        assertThatThrownBy(() -> windows.requireEnrollmentOpen(term, ENROLLMENT_START))
                .isInstanceOf(EnrollmentClosedException.class);
        assertThatThrownBy(() -> windows.requireAdjustmentOpen(term, ADJUSTMENT_START))
                .isInstanceOf(AdjustmentClosedException.class);
        assertThatThrownBy(() -> windows.requireRetakeOpen(term, ENROLLMENT_START))
                .isInstanceOf(EnrollmentClosedException.class);
    }

    @Test
    void retakeUsesTheEnrollmentWindowAndItsExactBoundaries() {
        Term term = activeTerm();

        assertThatCode(() -> windows.requireRetakeOpen(term, ENROLLMENT_START))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> windows.requireRetakeOpen(term, ENROLLMENT_END))
                .isInstanceOf(EnrollmentClosedException.class);
    }

    private static Term activeTerm() {
        return termWithStatus("ACTIVE");
    }

    private static Term termWithStatus(String status) {
        return new Term("term-1", "2026-2027-1", "Fall",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31),
                ENROLLMENT_START, ENROLLMENT_END, ADJUSTMENT_START, ADJUSTMENT_END,
                status, 0, null, null);
    }
}
