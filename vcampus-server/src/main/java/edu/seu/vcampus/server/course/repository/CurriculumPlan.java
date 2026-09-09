package edu.seu.vcampus.server.course.repository;

/** Published or draft curriculum version for one major and entry cohort. */
public record CurriculumPlan(String planId, String majorCode, int cohortYear,
                             String planName, int planVersion, String planStatus) { }
