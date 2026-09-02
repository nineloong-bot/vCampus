package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.Schedule;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ScheduleConflictPolicyTest {
    private final ScheduleConflictPolicy policy = new ScheduleConflictPolicy();

    @ParameterizedTest(name = "{index}: {2}")
    @MethodSource("scheduleCases")
    void detectsOnlyThreeDimensionalOverlap(Schedule a, Schedule b, boolean expected, String description) {
        assertThat(policy.conflicts(a, b))
                .as(description)
                .isEqualTo(expected);
    }

    static Stream<Arguments> scheduleCases() {
        return Stream.of(
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 16), s(DayOfWeek.MONDAY, 2, 3, 8, 12), true,
                        "same day with inclusive week and period overlap"),
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 7), s(DayOfWeek.MONDAY, 2, 3, 8, 12), false,
                        "periods are adjacent rather than overlapping"),
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 16), s(DayOfWeek.TUESDAY, 1, 2, 1, 16), false,
                        "different days do not conflict"),
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 16), s(DayOfWeek.MONDAY, 1, 2, 17, 18), false,
                        "periods do not overlap"),
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 16), s(DayOfWeek.MONDAY, 3, 4, 1, 16), false,
                        "weeks do not overlap"),
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 16), s(DayOfWeek.MONDAY, 2, 2, 16, 16), true,
                        "week and period endpoints overlap inclusively"),
                arguments(s(DayOfWeek.MONDAY, 1, 2, 1, 16), s(DayOfWeek.MONDAY, 2, 2, 17, 17), false,
                        "period endpoint just outside does not overlap"));
    }

    private static Schedule s(DayOfWeek day, int startPeriod, int endPeriod, int startWeek, int endWeek) {
        return new Schedule("schedule-" + day + startPeriod + endPeriod + startWeek + endWeek,
                "offering-1", day, startPeriod, endPeriod, startWeek, endWeek, "A101");
    }
}
