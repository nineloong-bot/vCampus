package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 学生身份凭据对象。
 *  *
 *  * @param studentId 学生内部标识
 *  * @param studentNumber 学号
 *  * @param realName 真实姓名
 */
public record StudentIdentity(String studentId, String userId, String campusCardNumber,
        String studentNumber, StudentType studentType, String majorId, String classId,
        StudentStatus status) implements Serializable { }
