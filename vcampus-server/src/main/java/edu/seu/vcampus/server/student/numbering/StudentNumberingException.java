package edu.seu.vcampus.server.student.numbering;

/** Numbering validation or capacity failure carrying its stable error code. */
public final class StudentNumberingException extends RuntimeException {
    private final String code;

    /**
     * Creates a student numbering exception with its required collaborators.
     * @param code the code
     * @param message the message
     */
    public StudentNumberingException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Performs the code operation.
     * @return the operation result
     */
    public String code() {
        return code;
    }
}
