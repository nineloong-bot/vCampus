package edu.seu.vcampus.server.course.repository;

/** Independent capacity bucket for retake students in a shared teaching class. */
public record RetakeQuota(String offeringId, int capacity, int enrolledCount) {
    /**
     * Creates a retake quota with its required collaborators.
     * @param offeringId the offering identifier
     * @param capacity the capacity
     * @param enrolledCount the enrolled count
     */
    public RetakeQuota {
        if (capacity < 0 || enrolledCount < 0 || enrolledCount > capacity) {
            throw new IllegalArgumentException("invalid retake quota");
        }
    }
}
