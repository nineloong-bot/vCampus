package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/** Complete serializable teaching offering with separate normal and retake quotas. */
/**
 * Carries immutable offering view data.
 * @param offeringId the offering identifier
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param enrolledCount the enrolled count
 * @param retakeCapacity the retake capacity
 * @param retakeEnrolledCount the retake enrolled count
 * @param offeringStatus the offering status
 * @param rowVersion the row version
 * @param createdAt the created at
 * @param updatedAt the updated at
 * @param schedules the schedules
 */
public record OfferingView(String offeringId, String termId, String courseId,
                           String teacherUserId, String className,
                           int capacity, int enrolledCount,
                           int retakeCapacity, int retakeEnrolledCount,
                           String offeringStatus, long rowVersion,
                           Instant createdAt, Instant updatedAt,
                           List<ScheduleItem> schedules) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /**
 * Validates and creates a offering view.
 * @param offeringId the offering identifier
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param enrolledCount the enrolled count
 * @param retakeCapacity the retake capacity
 * @param retakeEnrolledCount the retake enrolled count
 * @param offeringStatus the offering status
 * @param rowVersion the row version
 * @param createdAt the created at
 * @param updatedAt the updated at
 * @param schedules the schedules
 */
public OfferingView { schedules = List.copyOf(schedules); }

    /**
     * Validates and creates a offering view.
     * @param offeringId the offering identifier
     * @param termId the term identifier
     * @param courseId the course identifier
     * @param teacherUserId the teacher user identifier
     * @param className the class name
     * @param capacity the capacity
     * @param enrolledCount the enrolled count
     * @param offeringStatus the offering status
     * @param rowVersion the row version
     * @param createdAt the created at
     * @param updatedAt the updated at
     * @param schedules the schedules
     */
    public OfferingView(String offeringId, String termId, String courseId,
                        String teacherUserId, String className,
                        int capacity, int enrolledCount, String offeringStatus,
                        long rowVersion, Instant createdAt, Instant updatedAt,
                        List<ScheduleItem> schedules) {
        this(offeringId, termId, courseId, teacherUserId, className, capacity, enrolledCount,
                capacity, 0, offeringStatus, rowVersion, createdAt, updatedAt, schedules);
    }
}
