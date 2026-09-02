package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Admin command to update all academic fields of a student record. */
public record UpdateStudentAcademicCommand(String studentId, String studentNumber, String classId,
        StudentType studentType, StudentStatus status, Boolean enrolled, Boolean onCampus,
        String campus, String educationLevel, String trainingMode, Integer programLengthYears,
        AttendanceMode attendanceMode, String degreeName, String educationName,
        LocalDate expectedGraduationDate, LocalDate graduationDate, String studentSource,
        String graduateStudyMode, String counselorName, String counselorContact,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
