package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Identifiers and first-login state produced by a successful admission. */
public record StudentAdmissionResult(StudentView student, String campusCardNumber,
        String studentNumber, boolean mustChangePassword) implements Serializable { }
