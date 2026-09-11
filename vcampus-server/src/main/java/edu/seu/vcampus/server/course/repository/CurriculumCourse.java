package edu.seu.vcampus.server.course.repository;

import edu.seu.vcampus.common.course.AcademicSeason;

/** One catalog course placed into a published curriculum term. */
public record CurriculumCourse(String planCourseId, String planId, String courseId,
                               int academicYearNo, AcademicSeason season,
                               String courseNature, String courseCategory,
                               String offeringUnit) { }
