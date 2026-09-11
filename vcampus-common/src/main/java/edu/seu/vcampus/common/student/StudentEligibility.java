package edu.seu.vcampus.common.student;
import java.io.Serializable;
public record StudentEligibility(String studentId, StudentStatus status,
        boolean eligible, String reason, String majorCode, int cohortYear)
        implements Serializable {
    public StudentEligibility(String studentId, StudentStatus status,
            boolean eligible, String reason) {
        this(studentId, status, eligible, reason, null, 0);
    }
}
