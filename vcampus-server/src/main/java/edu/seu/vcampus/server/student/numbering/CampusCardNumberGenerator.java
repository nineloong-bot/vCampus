package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.persistence.TransactionContext;

/** Allocates the next global campus-card number in an existing transaction. */
public interface CampusCardNumberGenerator {
    /**
     * Performs the next operation.
     * @param transaction the transaction
     * @param studentType the student type
     * @param enrollmentYear the enrollment year
     * @return the operation result
     */
    String next(TransactionContext transaction, StudentType studentType, int enrollmentYear);
}
