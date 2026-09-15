package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** Offering row used by query lists, including independent normal and retake quotas. */
/**
 * Carries immutable offering summary data.
 * @param offeringId the offering identifier
 * @param termId the term identifier
 * @param courseId the course identifier
 * @param courseCode the course code
 * @param courseName the course name
 * @param teacherUserId the teacher user identifier
 * @param className the class name
 * @param capacity the capacity
 * @param enrolledCount the enrolled count
 * @param retakeCapacity the retake capacity
 * @param retakeEnrolledCount the retake enrolled count
 * @param offeringStatus the offering status
 * @param rowVersion the row version
 * @param schedules the schedules
 */
public record OfferingSummary(String offeringId, String termId, String courseId,
                              String courseCode, String courseName, String teacherUserId,
                              String className, int capacity, int enrolledCount,
                              int retakeCapacity, int retakeEnrolledCount,
                              String offeringStatus, long rowVersion,
                              List<ScheduleItem> schedules) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Validates and creates a offering summary.
     * @param offeringId the offering id
     * @param termId the term id
     * @param courseId the course id
     * @param courseCode the course code
     * @param courseName the course name
     * @param teacherUserId the teacher user id
     * @param className the class name
     * @param capacity the capacity
     * @param enrolledCount the enrolled count
     * @param retakeCapacity the retake capacity
     * @param retakeEnrolledCount the retake enrolled count
     * @param offeringStatus the offering status
     * @param rowVersion the row version
     * @param schedules the schedules
     */
    public OfferingSummary {
        schedules = List.copyOf(schedules);
        if (capacity < 0 || enrolledCount < 0 || enrolledCount > capacity
                || retakeCapacity < 0 || retakeEnrolledCount < 0
                || retakeEnrolledCount > retakeCapacity) {
            throw new IllegalArgumentException("invalid offering quota");
        }
    }

    /**
     * Creates a summary using the normal capacity as the legacy retake quota.
     * @param offeringId the offering identifier
     * @param termId the term identifier
     * @param courseId the course identifier
     * @param courseCode the course code
     * @param courseName the course name
     * @param teacherUserId the teacher user identifier
     * @param className the class name
     * @param capacity the capacity
     * @param enrolledCount the enrolled count
     * @param offeringStatus the offering status
     * @param rowVersion the row version
     * @param schedules the schedules
     */
    public OfferingSummary(String offeringId, String termId, String courseId,
                           String courseCode, String courseName, String teacherUserId,
                           String className, int capacity, int enrolledCount,
                           String offeringStatus, long rowVersion, List<ScheduleItem> schedules) {
        this(offeringId, termId, courseId, courseCode, courseName, teacherUserId, className,
                capacity, enrolledCount, capacity, 0, offeringStatus, rowVersion, schedules);
    }

    /**
 * Returns the normal remaining result.
 * @return the computed result
 */
public int normalRemaining() { return capacity - enrolledCount; }
    /**
 * Returns the retake remaining result.
 * @return the computed result
 */
public int retakeRemaining() { return retakeCapacity - retakeEnrolledCount; }
}
