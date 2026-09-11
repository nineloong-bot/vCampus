package edu.seu.vcampus.server.course.domain;

/** Raised when a student has identity context but no published matching curriculum. */
public final class CurriculumNotConfiguredException extends CourseRuleException {
    public static final String CODE = "CURRICULUM_NOT_CONFIGURED";
    public CurriculumNotConfiguredException() { super(CODE, "尚未配置适用的培养方案"); }
}
