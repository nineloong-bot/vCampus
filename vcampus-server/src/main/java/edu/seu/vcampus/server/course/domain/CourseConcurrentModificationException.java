package edu.seu.vcampus.server.course.domain;
/** Stable optimistic-lock conflict distinct from a missing course-owned entity. */
public class CourseConcurrentModificationException extends CourseRuleException {
    public static final String CODE = "COMMON_CONCURRENT_MODIFICATION";
    public CourseConcurrentModificationException() { super(CODE, CODE + ": record changed concurrently"); }
}
