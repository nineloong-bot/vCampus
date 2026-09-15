package edu.seu.vcampus.server.student.repository;

/** Business exception for training plan operations. */
public class TrainingPlanException extends RuntimeException {
    private final String code;

    /**
     * Creates a training plan exception with its required collaborators.
     * @param code the code
     * @param message the message
     */
    public TrainingPlanException(String code, String message) {
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
