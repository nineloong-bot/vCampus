package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * Carries immutable update student contact command data.
 * @param studentId the student identifier
 * @param email the email
 * @param phone the phone
 * @param expectedVersion the expected version
 */
public record UpdateStudentContactCommand(String studentId, String email,
        String phone, long expectedVersion) implements Serializable { }
