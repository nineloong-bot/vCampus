package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.CourseSummary;

import java.util.List;

/** Read-only cross-module contract for a student's current active course selections. */
public interface CourseQueryPort {
    /**
     * Performs the has active enrollment operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    boolean hasActiveEnrollment(String studentId);
    /**
     * Performs the find courses by student operation.
     * @param studentId the student identifier
     * @return the operation result
     */
    List<CourseSummary> findCoursesByStudent(String studentId);
}
