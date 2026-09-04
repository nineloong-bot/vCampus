package edu.seu.vcampus.server.course.domain;

/** A completed course cannot be selected again. */
public final class CourseAlreadyPassedException extends CourseRuleException {
    public static final String CODE = "COURSE_ALREADY_PASSED";
    public CourseAlreadyPassedException() { super(CODE, "course has already been passed"); }
}
