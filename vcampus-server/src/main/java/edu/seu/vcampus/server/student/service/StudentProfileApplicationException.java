package edu.seu.vcampus.server.student.service;

/** Domain validation failure in the draft and review lifecycle. */
public final class StudentProfileApplicationException extends RuntimeException {
    private final String code;

    public StudentProfileApplicationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
