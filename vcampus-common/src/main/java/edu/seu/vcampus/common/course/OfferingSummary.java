package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Offering row used by query lists, including independent normal and retake quotas. */
public record OfferingSummary(String offeringId, String termId, String courseId,
                              String courseCode, String courseName, String teacherUserId,
                              String className, int capacity, int enrolledCount,
                              int retakeCapacity, int retakeEnrolledCount,
                              String offeringStatus, long rowVersion,
                              List<ScheduleItem> schedules) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public OfferingSummary {
        schedules = List.copyOf(schedules);
        if (capacity < 0 || enrolledCount < 0 || enrolledCount > capacity
                || retakeCapacity < 0 || retakeEnrolledCount < 0
                || retakeEnrolledCount > retakeCapacity) {
            throw new IllegalArgumentException("invalid offering quota");
        }
    }

    public OfferingSummary(String offeringId, String termId, String courseId,
                           String courseCode, String courseName, String teacherUserId,
                           String className, int capacity, int enrolledCount,
                           String offeringStatus, long rowVersion, List<ScheduleItem> schedules) {
        this(offeringId, termId, courseId, courseCode, courseName, teacherUserId, className,
                capacity, enrolledCount, capacity, 0, offeringStatus, rowVersion, schedules);
    }

    public int normalRemaining() { return capacity - enrolledCount; }
    public int retakeRemaining() { return retakeCapacity - retakeEnrolledCount; }
}
