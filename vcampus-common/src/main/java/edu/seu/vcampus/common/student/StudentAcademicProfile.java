package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Approved academic information displayed on the self-service page. */
/**
 * Carries immutable student academic profile data.
 * @param studentCategory the student category
 * @param enrolled the enrolled
 * @param onCampus the on campus
 * @param academicStatus the academic status
 * @param campus the campus
 * @param currentGrade the current grade
 * @param departmentName the department name
 * @param majorName the major name
 * @param className the class name
 * @param educationLevel the education level
 * @param trainingMode the training mode
 * @param programLengthYears the program length years
 * @param attendanceMode the attendance mode
 * @param degreeName the degree name
 * @param educationName the education name
 * @param expectedGraduationDate the expected graduation date
 * @param graduationDate the graduation date
 * @param studentSource the student source
 * @param graduateStudyMode the graduate study mode
 * @param counselorName the counselor name
 * @param counselorContact the counselor contact
 */
public record StudentAcademicProfile(
        String studentCategory,
        boolean enrolled,
        boolean onCampus,
        String academicStatus,
        String campus,
        String currentGrade,
        String departmentName,
        String majorName,
        String className,
        String educationLevel,
        String trainingMode,
        Integer programLengthYears,
        AttendanceMode attendanceMode,
        String degreeName,
        String educationName,
        LocalDate expectedGraduationDate,
        LocalDate graduationDate,
        String studentSource,
        String graduateStudyMode,
        String counselorName,
        String counselorContact) implements Serializable { }
