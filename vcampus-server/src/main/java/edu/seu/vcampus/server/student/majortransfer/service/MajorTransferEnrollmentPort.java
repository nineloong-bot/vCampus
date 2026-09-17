package edu.seu.vcampus.server.student.majortransfer.service;

import java.sql.Connection;
import java.time.Instant;

/** Reconciles course enrollments when an approved major transfer becomes effective. */
@FunctionalInterface
public interface MajorTransferEnrollmentPort {
    /** Validates that the target curriculum needed for effectuation exists. */
    default void validate(Connection connection, String targetMajorCode, int cohortYear) { }

    /**
     * Drops current-term enrollments that are absent from the target curriculum.
     *
     * @param connection caller-owned transaction connection
     * @param studentId transferred student's internal identifier
     * @param targetMajorCode target major code
     * @param cohortYear student's original enrollment year
     * @param operatorUserId administrator making the transfer effective
     * @param occurredAt effective timestamp used by enrollment and audit rows
     * @return reconciliation totals
     */
    Reconciliation reconcile(Connection connection, String studentId, String targetMajorCode,
                             int cohortYear, String operatorUserId, Instant occurredAt);

    /** Result of reconciling one student's active enrollments. */
    record Reconciliation(int droppedEnrollments) {
        /** Validates that the reported count is non-negative. */
        public Reconciliation {
            if (droppedEnrollments < 0) {
                throw new IllegalArgumentException("droppedEnrollments must not be negative");
            }
        }
    }

    /** Returns an adapter suitable for deployments where course reconciliation is disabled. */
    static MajorTransferEnrollmentPort noOp() {
        return (connection, studentId, targetMajorCode, cohortYear, operatorUserId, occurredAt) ->
                new Reconciliation(0);
    }
}
