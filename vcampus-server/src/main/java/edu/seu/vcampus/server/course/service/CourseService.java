package edu.seu.vcampus.server.course.service;

import edu.seu.vcampus.common.course.EnrollCommand;
import edu.seu.vcampus.common.course.EnrollmentView;
import edu.seu.vcampus.common.course.LateAddCommand;
import edu.seu.vcampus.common.course.DropCommand;
import edu.seu.vcampus.common.course.ChangeOfferingCommand;

/** Application operations owned by the course module. */
public interface CourseService {
    /** Enrolls the authenticated student in an offering during the normal window. */
    EnrollmentView enroll(String sessionToken, EnrollCommand command);

    /** Adds the authenticated student during the adjustment window. */
    EnrollmentView addDuringAdjustment(String sessionToken, LateAddCommand command);

    /** Drops the authenticated student's active enrollment during the adjustment window. */
    void dropDuringAdjustment(String sessionToken, DropCommand command);

    /** Atomically changes the authenticated student's active enrollment to another offering. */
    EnrollmentView changeDuringAdjustment(String sessionToken, ChangeOfferingCommand command);
}
