package edu.seu.vcampus.server.student.service;

/** Stable business error raised before allocating admission identifiers. */
public final class StudentAdmissionException extends RuntimeException {
    private final String code;
    public StudentAdmissionException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
