package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** A single student row in a batch import, with server-assigned class index. */
/**
 * Carries immutable batch student entry data.
 * @param campusCardNumber the campus card number
 * @param studentName the student name
 * @param gender the gender
 * @param comprehensiveScore the comprehensive score
 * @param classIndex the class index
 */
public record BatchStudentEntry(
        String campusCardNumber,
        String studentName,
        String gender,
        double comprehensiveScore,
        int classIndex) implements Serializable { }
