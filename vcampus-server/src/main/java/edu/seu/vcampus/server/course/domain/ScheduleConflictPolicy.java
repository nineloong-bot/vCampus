package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.Schedule;

import java.util.Objects;

/** Determines whether two weekly schedule rows occupy any common slot. */
public final class ScheduleConflictPolicy {
    /**
     * A conflict requires overlap on all three dimensions: day, week, and period.
     * Week and period endpoints are inclusive because a schedule row describes the
     * numbered sessions it occupies.
     */
    public boolean conflicts(Schedule a, Schedule b) {
        Objects.requireNonNull(a, "first schedule");
        Objects.requireNonNull(b, "second schedule");
        return a.dayOfWeek() == b.dayOfWeek()
                && a.startWeek() <= b.endWeek() && b.startWeek() <= a.endWeek()
                && a.startPeriod() <= b.endPeriod() && b.startPeriod() <= a.endPeriod();
    }
}
