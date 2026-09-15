package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Administrator-supplied identifiers and core fields for manual student creation. */
/**
 * Carries immutable create student manual command data.
 * @param campusCardNumber the campus card number
 * @param studentNumber the student number
 * @param studentName the student name
 * @param gender the gender
 * @param studentType the student type
 * @param idDocumentType the id document type
 * @param idDocumentNumber the id document number
 * @param birthDate the birth date
 * @param enrollmentDate the enrollment date
 * @param classId the class identifier
 */
public record CreateStudentManualCommand(
        String campusCardNumber,
        String studentNumber,
        String studentName,
        String gender,
        StudentType studentType,
        String idDocumentType,
        String idDocumentNumber,
        LocalDate birthDate,
        LocalDate enrollmentDate,
        String classId) implements Serializable { }
