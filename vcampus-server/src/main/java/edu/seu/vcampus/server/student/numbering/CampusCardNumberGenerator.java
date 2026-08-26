package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.common.student.StudentType;
import edu.seu.vcampus.server.persistence.TransactionContext;

/** Allocates the next global campus-card number in an existing transaction. */
public interface CampusCardNumberGenerator {
    String next(TransactionContext transaction, StudentType studentType, int enrollmentYear);
}
