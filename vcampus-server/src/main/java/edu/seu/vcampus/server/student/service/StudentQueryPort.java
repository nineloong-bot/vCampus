package edu.seu.vcampus.server.student.service;
import edu.seu.vcampus.common.student.StudentEligibility;
import edu.seu.vcampus.common.student.StudentIdentity;
/**
 * 学籍模块对外导出的学生信息狭窄只读查询端口。
 */
public interface StudentQueryPort {
    StudentIdentity findByUserId(String userId);
    StudentEligibility getEnrollmentEligibility(String userId);
    StudentEligibility getEnrollmentEligibilityByStudentNumber(String studentNumber);
    boolean existsActiveStudent(String studentId);
}
