package edu.seu.vcampus.common.student;
import java.io.Serializable;
/**
 * 学生或管理员更新联系方式的请求命令。
 *  *
 *  * @param studentId 学生标识
 *  * @param phone 联系电话
 *  * @param email 电子邮箱
 *  * @param address 通讯地址
 */
public record UpdateStudentContactCommand(String studentId, String email,
        String phone, long expectedVersion) implements Serializable { }
