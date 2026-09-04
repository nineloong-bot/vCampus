package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.time.LocalDate;

/** Administrator-supplied identifiers and core fields for manual student creation. */
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
