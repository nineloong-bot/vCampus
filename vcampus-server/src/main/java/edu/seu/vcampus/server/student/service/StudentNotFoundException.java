package edu.seu.vcampus.server.student.service;
public final class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException() { super("Student not found"); }
}
