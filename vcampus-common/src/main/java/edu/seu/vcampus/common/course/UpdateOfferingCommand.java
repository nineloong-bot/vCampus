package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Updates an offering while preserving its server-owned academic term. */
public record UpdateOfferingCommand(String offeringId, String courseId, String teacherUserId,
                                    String className, int capacity, int retakeCapacity,
                                    String offeringStatus, long expectedVersion,
                                    List<CreateOfferingCommand.ScheduleInput> schedules)
        implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates all mutable offering fields. */
    public UpdateOfferingCommand {
        Objects.requireNonNull(offeringId);
        new CreateOfferingCommand(courseId, teacherUserId, className, capacity,
                retakeCapacity, offeringStatus, schedules);
        schedules = List.copyOf(schedules);
        CourseValidation.text("offeringId", offeringId, 36);
        if (expectedVersion < 0) throw new IllegalArgumentException("invalid offering");
    }

    /** Updates an offering with the default retake quota. */
    public UpdateOfferingCommand(String offeringId, String courseId, String teacherUserId,
                                 String className, int capacity, String offeringStatus,
                                 long expectedVersion,
                                 List<CreateOfferingCommand.ScheduleInput> schedules) {
        this(offeringId, courseId, teacherUserId, className, capacity, 5,
                offeringStatus, expectedVersion, schedules);
    }

    /**
     * Compatibility constructor that ignores the former client-owned term identifier.
     * @deprecated use the constructor without {@code termId}
     */
    @Deprecated
    public UpdateOfferingCommand(String offeringId, String termId, String courseId,
                                 String teacherUserId, String className, int capacity,
                                 int retakeCapacity, String offeringStatus, long expectedVersion,
                                 List<CreateOfferingCommand.ScheduleInput> schedules) {
        this(offeringId, courseId, teacherUserId, className, capacity, retakeCapacity,
                offeringStatus, expectedVersion, schedules);
    }

    /**
     * Compatibility constructor that ignores the former term and uses the default retake quota.
     * @deprecated use the constructor without {@code termId}
     */
    @Deprecated
    public UpdateOfferingCommand(String offeringId, String termId, String courseId,
                                 String teacherUserId, String className, int capacity,
                                 String offeringStatus, long expectedVersion,
                                 List<CreateOfferingCommand.ScheduleInput> schedules) {
        this(offeringId, courseId, teacherUserId, className, capacity, 5,
                offeringStatus, expectedVersion, schedules);
    }
}
