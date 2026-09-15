package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Creates an offering in the server-selected active term. */
public record CreateOfferingCommand(String courseId, String teacherUserId, String className,
                                    int capacity, int retakeCapacity, String offeringStatus,
                                    List<ScheduleInput> schedules) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates all client-owned offering fields. */
    public CreateOfferingCommand {
        Objects.requireNonNull(courseId);
        Objects.requireNonNull(teacherUserId);
        Objects.requireNonNull(className);
        Objects.requireNonNull(offeringStatus);
        schedules = List.copyOf(Objects.requireNonNull(schedules));
        CourseValidation.text("courseId", courseId, 36);
        CourseValidation.text("teacherUserId", teacherUserId, 36);
        CourseValidation.text("className", className, 64);
        if (capacity < 1 || retakeCapacity < 0
                || !Set.of("DRAFT", "OPEN", "CLOSED", "CANCELLED").contains(offeringStatus)) {
            throw new IllegalArgumentException("invalid offering");
        }
    }

    /** Creates an offering with the default retake quota. */
    public CreateOfferingCommand(String courseId, String teacherUserId, String className,
                                 int capacity, String offeringStatus, List<ScheduleInput> schedules) {
        this(courseId, teacherUserId, className, capacity, 5, offeringStatus, schedules);
    }

    /**
     * Compatibility constructor that ignores the former client-owned term identifier.
     * @deprecated use the constructor without {@code termId}
     */
    @Deprecated
    public CreateOfferingCommand(String termId, String courseId, String teacherUserId,
                                 String className, int capacity, int retakeCapacity,
                                 String offeringStatus, List<ScheduleInput> schedules) {
        this(courseId, teacherUserId, className, capacity, retakeCapacity, offeringStatus, schedules);
    }

    /**
     * Compatibility constructor that ignores the former term and uses the default retake quota.
     * @deprecated use the constructor without {@code termId}
     */
    @Deprecated
    public CreateOfferingCommand(String termId, String courseId, String teacherUserId,
                                 String className, int capacity, String offeringStatus,
                                 List<ScheduleInput> schedules) {
        this(courseId, teacherUserId, className, capacity, 5, offeringStatus, schedules);
    }

    /** Describes one recurring schedule row. */
    public record ScheduleInput(String dayOfWeek, int startPeriod, int endPeriod,
                                int startWeek, int endWeek, String classroom)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;

        /** Validates schedule boundaries and weekday names. */
        public ScheduleInput {
            Objects.requireNonNull(dayOfWeek);
            Objects.requireNonNull(classroom);
            try {
                java.time.DayOfWeek.valueOf(dayOfWeek.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("invalid schedule", error);
            }
            CourseValidation.text("classroom", classroom, 64);
            if (startPeriod < 1 || endPeriod < startPeriod
                    || startWeek < 1 || endWeek < startWeek) {
                throw new IllegalArgumentException("invalid schedule");
            }
        }
    }
}
