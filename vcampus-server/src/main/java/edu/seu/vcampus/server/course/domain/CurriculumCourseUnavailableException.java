package edu.seu.vcampus.server.course.domain;

/** Prevents enrollment by offering id when the course is outside the student's current plan. */
public final class CurriculumCourseUnavailableException extends CourseRuleException {
    public static final String CODE = "CURRICULUM_COURSE_UNAVAILABLE";
    public CurriculumCourseUnavailableException() { super(CODE, "该课程不在当前学期可选范围内"); }
}
