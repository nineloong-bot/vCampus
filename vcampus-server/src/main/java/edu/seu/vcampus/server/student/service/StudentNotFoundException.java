package edu.seu.vcampus.server.student.service;
/**
 * 学生档案或学籍记录不存在时抛出的业务异常。
 */
public final class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException() { super("Student not found"); }
}
