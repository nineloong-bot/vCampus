package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Complete serializable teaching offering with separate normal and retake quotas. */
public record OfferingView(String offeringId, String termId, String courseId,
                           String teacherUserId, String className,
                           int capacity, int enrolledCount,
                           int retakeCapacity, int retakeEnrolledCount,
                           String offeringStatus, long rowVersion,
                           Instant createdAt, Instant updatedAt,
                           List<ScheduleItem> schedules) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    public OfferingView { schedules = List.copyOf(schedules); }

    public OfferingView(String offeringId, String termId, String courseId,
                        String teacherUserId, String className,
                        int capacity, int enrolledCount, String offeringStatus,
                        long rowVersion, Instant createdAt, Instant updatedAt,
                        List<ScheduleItem> schedules) {
        this(offeringId, termId, courseId, teacherUserId, className, capacity, enrolledCount,
                capacity, 0, offeringStatus, rowVersion, createdAt, updatedAt, schedules);
    }
}
