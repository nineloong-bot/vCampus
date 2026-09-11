package edu.seu.vcampus.common.course;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test void academicTermCarriesCurriculumSeasonMapping() {
        TermView term = new TermView("term", "2026-AUTUMN", "2026-2027学年秋季学期",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 15), 2026,
                AcademicSeason.AUTUMN, Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-10T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:00Z"), "ACTIVE", 0,
                Instant.EPOCH, Instant.EPOCH);

        assertThat(term.academicYearStart()).isEqualTo(2026);
        assertThat(term.season().curriculumTermOrdinal()).isEqualTo(2);
        assertThatThrownBy(() -> new CreateTermCommand("bad", "bad", term.startDate(),
                term.endDate(), 1999, AcademicSeason.AUTUMN, term.enrollmentStartAt(),
                term.enrollmentEndAt(), term.adjustmentStartAt(), term.adjustmentEndAt(), "ACTIVE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void offeringViewsKeepNormalAndRetakeQuotasIndependent() {
        OfferingSummary summary = new OfferingSummary("o", "term", "course", "B09D0012",
                "数据库原理", "teacher", "01班", 40, 40, 6, 2,
                "OPEN", 0, List.of());

        assertThat(summary.normalRemaining()).isZero();
        assertThat(summary.retakeRemaining()).isEqualTo(4);
        assertThatThrownBy(() -> new OfferingSummary("o", "term", "course", "B09D0012",
                "数据库原理", "teacher", "01班", 40, 41, 6, 2,
                "OPEN", 0, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offeringWithRetakeCapacity(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CreateOfferingCommand offering(String status, List<CreateOfferingCommand.ScheduleInput> schedules) {
        return new CreateOfferingCommand("term", "course", "teacher", "A", 20, status, schedules);
    }

    private static CreateOfferingCommand offeringWithRetakeCapacity(int retakeCapacity) {
        return new CreateOfferingCommand("term", "course", "teacher", "A", 20,
                retakeCapacity, "OPEN", List.of());
    }

    private static CreateOfferingCommand.ScheduleInput schedule(int startPeriod, int endPeriod,
                                                                 int startWeek, int endWeek, String room) {
        return new CreateOfferingCommand.ScheduleInput("MONDAY", startPeriod, endPeriod, startWeek, endWeek, room);
    }
}
