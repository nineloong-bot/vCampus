package edu.seu.vcampus.server.course.domain;

/** Prevents structural edits that would invalidate students already enrolled in an offering. */
public final class OfferingHasEnrollmentsException extends CourseRuleException {
    public static final String CODE = "COURSE_OFFERING_HAS_ENROLLMENTS";

    /**
     * Creates a offering has enrollments exception with its required collaborators.
     */
    public OfferingHasEnrollmentsException() {
        super(CODE, "已有学生选课，不能修改所属学期、课程或上课安排");
    }
}
