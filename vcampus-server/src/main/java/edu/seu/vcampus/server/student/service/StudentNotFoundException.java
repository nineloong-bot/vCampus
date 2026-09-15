package edu.seu.vcampus.server.student.service;
/** Provides student not found exception behavior. */
public final class StudentNotFoundException extends RuntimeException {
    public StudentNotFoundException() { super("Student not found"); }
}
