package edu.seu.vcampus.server.student.majortransfer.service;

/** Thrown when a major-transfer operation violates business rules. */
public final class MajorTransferException extends RuntimeException {
    private final String code;

    /**
     * Creates a major transfer exception with its required collaborators.
     * @param code the code
     * @param message the message
     */
    public MajorTransferException(String code, String message) {
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
