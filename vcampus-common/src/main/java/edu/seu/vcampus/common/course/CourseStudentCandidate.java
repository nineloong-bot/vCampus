package edu.seu.vcampus.common.course;

import java.io.Serial;
import java.io.Serializable;

/**
 * Student identity shown while a course administrator selects a class member.
 *
 * @param studentNumber stable student number
 * @param studentName student name shown for confirmation
 * @param className current administrative class name
 */
public record CourseStudentCandidate(String studentNumber, String studentName,
                                     String className) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    /** Validates the stable student number returned by the student module. */
    public CourseStudentCandidate {
        CourseValidation.text("studentNumber", studentNumber, 32);
        CourseValidation.text("studentName", studentName, 64);
    }
}
