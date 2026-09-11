package edu.seu.vcampus.server.course.repository;

/** Independent capacity bucket for retake students in a shared teaching class. */
public record RetakeQuota(String offeringId, int capacity, int enrolledCount) {
    public RetakeQuota {
        if (capacity < 0 || enrolledCount < 0 || enrolledCount > capacity) {
            throw new IllegalArgumentException("invalid retake quota");
        }
    }
}
