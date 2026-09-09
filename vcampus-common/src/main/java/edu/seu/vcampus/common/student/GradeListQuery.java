package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for listing grades by student or by course. */
public record GradeListQuery(
        String studentId,
        String planCourseId) implements Serializable { }
