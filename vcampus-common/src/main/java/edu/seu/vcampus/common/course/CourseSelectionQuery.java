package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Student query whose pages count distinct courses rather than teaching offerings. */
public record CourseSelectionQuery(String termId, String keyword, String weekday,
                                   Boolean conflict, String courseNature, String courseCategory,
                                   int page, int pageSize) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private static final Set<String> WEEKDAYS = Set.of(
            "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    private static final Set<String> NATURES = Set.of("REQUIRED", "RESTRICTED", "ELECTIVE");

    public CourseSelectionQuery {
        CourseValidation.text("termId", Objects.requireNonNull(termId, "termId"), 36);
        Objects.requireNonNull(keyword, "keyword");
        CourseValidation.optionalText("keyword", keyword, 128);
        CourseValidation.optionalText("courseCategory", courseCategory, 64);
        if ((weekday != null && !WEEKDAYS.contains(weekday))
                || (courseNature != null && !NATURES.contains(courseNature))
                || page < 0 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("invalid course selection query");
        }
    }

    public CourseSelectionQuery(String termId, String keyword, String weekday,
                                int page, int pageSize) {
        this(termId, keyword, weekday, null, null, null, page, pageSize);
    }
}
