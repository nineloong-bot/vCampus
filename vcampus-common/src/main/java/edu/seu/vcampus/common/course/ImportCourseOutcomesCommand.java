package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Imports coarse pass/fail outcomes from an external authoritative source. */
/**
 * Carries immutable import course outcomes command data.
 * @param outcomes the outcomes
 */
public record ImportCourseOutcomesCommand(List<OutcomeEntry> outcomes) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    /**
     * Validates and creates a import course outcomes command.
     * @param outcomes the outcomes
     */
    public ImportCourseOutcomesCommand {
        Objects.requireNonNull(outcomes, "outcomes");
        outcomes = List.copyOf(outcomes);
        if (outcomes.isEmpty()) throw new IllegalArgumentException("outcomes must not be empty");
    }

    /** One externally identified result; deliberately contains no grade or score. */
    /**
 * Carries immutable outcome entry data.
 * @param studentId the student identifier
 * @param courseId the course identifier
 * @param termId the term identifier
 * @param outcome the outcome
 * @param sourceReference the source reference
 */
public record OutcomeEntry(String studentId, String courseId, String termId,
                               CourseOutcome outcome, String sourceReference)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        /**
         * Validates and creates a outcome entry.
         * @param studentId the student id
         * @param courseId the course id
         * @param termId the term id
         * @param outcome the outcome
         * @param sourceReference the source reference
         */
        public OutcomeEntry {
            Objects.requireNonNull(studentId, "studentId");
            Objects.requireNonNull(courseId, "courseId");
            Objects.requireNonNull(termId, "termId");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(sourceReference, "sourceReference");
            CourseValidation.text("studentId", studentId, 36);
            CourseValidation.text("courseId", courseId, 36);
            CourseValidation.text("termId", termId, 36);
            CourseValidation.text("sourceReference", sourceReference, 128);
        }
    }
}
