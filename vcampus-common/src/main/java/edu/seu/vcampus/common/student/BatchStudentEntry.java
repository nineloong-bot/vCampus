package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** A single student row in a batch import, with server-assigned class index. */
public record BatchStudentEntry(
        String campusCardNumber,
        String studentName,
        String gender,
        double comprehensiveScore,
        int classIndex) implements Serializable { }
