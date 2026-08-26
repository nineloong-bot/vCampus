package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;

/** Application operations owned by the course module. */
public interface CourseService {
    /** Enrolls the authenticated student in an offering during the normal window. */
    EnrollmentView enroll(String sessionToken, EnrollCommand command);
}
