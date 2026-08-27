package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record StudentEligibility(String studentId, StudentStatus status,
        boolean eligible, String reason) implements Serializable { }
