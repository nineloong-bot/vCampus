package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 学生选课资格与档案有效性校验结果对象。
 */
public record StudentEligibility(String studentId, StudentStatus status,
        boolean eligible, String reason, String majorCode, int cohortYear)
        implements Serializable {
    public StudentEligibility(String studentId, StudentStatus status,
            boolean eligible, String reason) {
        this(studentId, status, eligible, reason, null, 0);
    }
}
