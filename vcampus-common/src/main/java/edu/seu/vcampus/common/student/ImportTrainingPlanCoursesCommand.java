package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Command to batch-import courses into a training plan. */
public record ImportTrainingPlanCoursesCommand(
        String planId,
        List<CourseEntry> courses) implements Serializable {

    public record CourseEntry(
            String courseCode,
            String courseName,
            BigDecimal credits,
            CourseType courseType,
            int semester) implements Serializable { }
}
