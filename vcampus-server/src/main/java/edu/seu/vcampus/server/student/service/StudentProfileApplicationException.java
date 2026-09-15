package edu.seu.vcampus.server.student.service;

/** Domain validation failure in the draft and review lifecycle. */
public final class StudentProfileApplicationException extends RuntimeException {
    private final String code;

    /**
     * Creates a student profile application exception with its required collaborators.
     * @param code the code
     * @param message the message
     */
    public StudentProfileApplicationException(String code, String message) {
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
