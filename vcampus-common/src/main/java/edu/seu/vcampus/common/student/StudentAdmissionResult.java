package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Identifiers and first-login state produced by a successful admission. */
/**
 * Carries immutable student admission result data.
 * @param student the student
 * @param campusCardNumber the campus card number
 * @param studentNumber the student number
 * @param mustChangePassword the must change password
 */
public record StudentAdmissionResult(StudentView student, String campusCardNumber,
        String studentNumber, boolean mustChangePassword) implements Serializable { }
