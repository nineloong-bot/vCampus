package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Query for listing grades by student or by course. */
/**
 * Carries immutable grade list query data.
 * @param studentId the student identifier
 * @param planCourseId the plan course identifier
 */
public record GradeListQuery(
        String studentId,
        String planCourseId) implements Serializable { }
