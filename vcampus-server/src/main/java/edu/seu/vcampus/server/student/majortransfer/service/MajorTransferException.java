package edu.seu.vcampus.server.student.majortransfer.service;

/** Thrown when a major-transfer operation violates business rules. */
public final class MajorTransferException extends RuntimeException {
    private final String code;

    public MajorTransferException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
