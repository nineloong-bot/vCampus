package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Schedule;

import java.sql.Connection;
import java.util.List;

/** Enforces classroom existence, capacity, and non-overlapping occupancy. */
public final class ClassroomSchedulingPolicy {
    private final CourseRepository repository;

    /** Creates the policy over the course repository boundary. */
    public ClassroomSchedulingPolicy(CourseRepository repository) { this.repository = repository; }

    /** Validates every submitted schedule before the offering mutation is persisted. */
    public void validate(Connection connection, String termId, String excludedOfferingId,
                         int offeringCapacity, List<Schedule> schedules) {
        for (int index = 0; index < schedules.size(); index++) {
            Schedule candidate = schedules.get(index);
            var room = repository.findClassroom(connection, candidate.classroom())
                    .orElseThrow(() -> new ClassroomRuleException("COURSE_CLASSROOM_NOT_FOUND"));
            if (!room.active()) throw new ClassroomRuleException("COURSE_CLASSROOM_NOT_FOUND");
            if (offeringCapacity > room.capacity()) {
                throw new ClassroomRuleException("COURSE_CLASSROOM_CAPACITY_EXCEEDED");
            }
            for (Schedule occupied : repository.findClassroomSchedules(
                    connection, termId, candidate.classroom(), excludedOfferingId)) {
                if (overlaps(candidate, occupied)) {
                    throw new ClassroomRuleException("COURSE_CLASSROOM_CONFLICT");
                }
            }
            for (int previous = 0; previous < index; previous++) {
                Schedule other = schedules.get(previous);
                if (candidate.classroom().equals(other.classroom()) && overlaps(candidate, other)) {
                    throw new ClassroomRuleException("COURSE_CLASSROOM_CONFLICT");
                }
            }
        }
    }

    private static boolean overlaps(Schedule left, Schedule right) {
        return left.dayOfWeek() == right.dayOfWeek()
                && left.startPeriod() <= right.endPeriod() && right.startPeriod() <= left.endPeriod()
                && left.startWeek() <= right.endWeek() && right.startWeek() <= left.endWeek();
    }
}
