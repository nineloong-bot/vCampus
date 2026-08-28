package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.CourseSummary;

import java.util.List;

/** Read-only cross-module contract for a student's current active course selections. */
public interface CourseQueryPort {
    boolean hasActiveEnrollment(String studentId);
    List<CourseSummary> findCoursesByStudent(String studentId);
}
