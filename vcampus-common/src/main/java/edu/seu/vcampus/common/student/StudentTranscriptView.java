package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Complete transcript for a student with summary statistics. */
/**
 * Carries immutable student transcript view data.
 * @param studentId the student identifier
 * @param studentName the student name
 * @param studentNumber the student number
 * @param majorName the major name
 * @param enrollmentYear the enrollment year
 * @param minElectiveCount the min elective count
 * @param minElectiveCredits the min elective credits
 * @param grades the grades
 * @param requiredPassed the required passed
 * @param requiredTotal the required total
 * @param electivePassed the elective passed
 * @param electiveTotal the elective total
 * @param creditsEarned the credits earned
 */
public record StudentTranscriptView(
        String studentId,
        String studentName,
        String studentNumber,
        String majorName,
        int enrollmentYear,
        long minElectiveCount,
        BigDecimal minElectiveCredits,
        List<StudentGradeView> grades,
        int requiredPassed,
        int requiredTotal,
        int electivePassed,
        int electiveTotal,
        BigDecimal creditsEarned) implements Serializable { }
