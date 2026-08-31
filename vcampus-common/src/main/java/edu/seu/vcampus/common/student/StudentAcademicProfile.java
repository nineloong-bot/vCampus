package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Approved academic information displayed on the self-service page. */
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
