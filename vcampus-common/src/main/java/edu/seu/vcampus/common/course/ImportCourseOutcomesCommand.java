package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Imports coarse pass/fail outcomes from an external authoritative source. */
public record ImportCourseOutcomesCommand(List<OutcomeEntry> outcomes) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    public ImportCourseOutcomesCommand {
        Objects.requireNonNull(outcomes, "outcomes");
        outcomes = List.copyOf(outcomes);
        if (outcomes.isEmpty()) throw new IllegalArgumentException("outcomes must not be empty");
    }

    /** One externally identified result; deliberately contains no grade or score. */
    public record OutcomeEntry(String studentId, String courseId, String termId,
                               CourseOutcome outcome, String sourceReference)
            implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
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
