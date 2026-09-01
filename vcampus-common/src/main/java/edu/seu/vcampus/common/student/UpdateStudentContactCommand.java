package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record UpdateStudentContactCommand(String studentId, String email,
        String phone, long expectedVersion) implements Serializable { }
