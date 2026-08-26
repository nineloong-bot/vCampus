package edu.seu.vcampus.server.student.numbering;

/** Numbering validation or capacity failure carrying its stable error code. */
public final class StudentNumberingException extends RuntimeException {
    private final String code;

    public StudentNumberingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
