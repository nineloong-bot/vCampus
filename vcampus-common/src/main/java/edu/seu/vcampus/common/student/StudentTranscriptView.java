package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Complete transcript for a student with summary statistics. */
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
