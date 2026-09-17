package edu.seu.vcampus.server.course.domain;

import edu.seu.vcampus.server.course.repository.CourseRepository;
import edu.seu.vcampus.server.course.repository.Schedule;

import java.sql.Connection;
import java.util.List;

/** Validates classroom capacity, room occupancy, and teacher availability. */
public final class OfferingSchedulingPolicy {
    private final CourseRepository repository;
    private final ScheduleConflictPolicy overlaps = new ScheduleConflictPolicy();

    /** Creates the policy over the course repository boundary. */
    public OfferingSchedulingPolicy(CourseRepository repository) { this.repository = repository; }

    /** Validates every submitted row before an offering mutation is persisted. */
    public void validate(Connection connection, String termId, String excludedOfferingId,
                         String teacherUserId, boolean physicalEducation,
                         int offeringCapacity, List<Schedule> schedules) {
        List<Schedule> teacherRows = repository.findTeacherSchedules(
                connection, termId, teacherUserId, excludedOfferingId);
        for (int index = 0; index < schedules.size(); index++) {
            Schedule candidate = schedules.get(index);
            var room = repository.findClassroom(connection, candidate.classroom())
                    .filter(value -> value.active())
                    .orElseThrow(() -> new SchedulingRuleException("COURSE_CLASSROOM_NOT_FOUND"));
            if (room.capacity() == Integer.MAX_VALUE && !room.sharedSportsVenue()) continue;
            if (room.sharedSportsVenue() && !physicalEducation) {
                throw new SchedulingRuleException("COURSE_CLASSROOM_SPORTS_ONLY");
            }
            if (!room.sharedSportsVenue() && offeringCapacity > room.capacity()) {
                throw new SchedulingRuleException("COURSE_CLASSROOM_CAPACITY_EXCEEDED");
            }
            if (!room.sharedSportsVenue() && repository.findClassroomSchedules(connection, termId,
                    candidate.classroom(), excludedOfferingId).stream().anyMatch(row -> overlaps.conflicts(candidate, row))) {
                throw new SchedulingRuleException("COURSE_CLASSROOM_CONFLICT");
            }
            if (teacherRows.stream().anyMatch(row -> overlaps.conflicts(candidate, row))) {
                throw new SchedulingRuleException("COURSE_TEACHER_CONFLICT");
            }
            for (int previous = 0; previous < index; previous++) {
                Schedule other = schedules.get(previous);
                if (overlaps.conflicts(candidate, other)) {
                    if (candidate.classroom().equals(other.classroom()) && !room.sharedSportsVenue()) {
                        throw new SchedulingRuleException("COURSE_CLASSROOM_CONFLICT");
                    }
                    throw new SchedulingRuleException("COURSE_TEACHER_CONFLICT");
                }
            }
        }
    }
}
