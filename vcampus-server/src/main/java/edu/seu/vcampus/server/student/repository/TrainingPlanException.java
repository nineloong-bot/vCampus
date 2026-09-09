package edu.seu.vcampus.server.student.repository;

/** Business exception for training plan operations. */
public class TrainingPlanException extends RuntimeException {
    private final String code;

    public TrainingPlanException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
