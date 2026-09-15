package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Admin command to update all academic fields of a student record. */
/**
 * Carries immutable update student academic command data.
 * @param studentId the student identifier
 * @param studentNumber the student number
 * @param classId the class identifier
 * @param studentType the student type
 * @param status the status
 * @param enrolled the enrolled
 * @param onCampus the on campus
 * @param campus the campus
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
 * @param effectiveDate the effective date
 * @param reason the reason
 * @param expectedVersion the expected version
 */
public record UpdateStudentAcademicCommand(String studentId, String studentNumber, String classId,
        StudentType studentType, StudentStatus status, Boolean enrolled, Boolean onCampus,
        String campus, String educationLevel, String trainingMode, Integer programLengthYears,
        AttendanceMode attendanceMode, String degreeName, String educationName,
        LocalDate expectedGraduationDate, LocalDate graduationDate, String studentSource,
        String graduateStudyMode, String counselorName, String counselorContact,
        LocalDate effectiveDate, String reason, long expectedVersion) implements Serializable { }
