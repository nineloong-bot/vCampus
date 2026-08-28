package edu.seu.vcampus.common.course;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseAdminDtoTest {
    @Test void rejectsInvalidOfferingStatusAndScheduleRanges() {
        assertThatThrownBy(() -> offering("UNKNOWN", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offering("OPEN", List.of(schedule(0, 1, 1, 1, "A101")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offering("OPEN", List.of(schedule(2, 1, 1, 1, "A101")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offering("OPEN", List.of(schedule(1, 1, 2, 1, "A101")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offering("OPEN", List.of(schedule(1, 1, 1, 1, " ")))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void updateValidatesAllAggregateFields() {
        assertThatThrownBy(() -> new UpdateOfferingCommand("o", "", "c", "teacher", "A", 20,
                "OPEN", 0, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    private static CreateOfferingCommand offering(String status, List<CreateOfferingCommand.ScheduleInput> schedules) {
        return new CreateOfferingCommand("term", "course", "teacher", "A", 20, status, schedules);
    }

    private static CreateOfferingCommand.ScheduleInput schedule(int startPeriod, int endPeriod,
                                                                 int startWeek, int endWeek, String room) {
        return new CreateOfferingCommand.ScheduleInput("MONDAY", startPeriod, endPeriod, startWeek, endWeek, room);
    }
}
