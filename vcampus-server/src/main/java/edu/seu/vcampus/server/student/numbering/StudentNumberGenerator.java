package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.server.persistence.TransactionContext;

/** Allocates the next student number for one major/year/class sequence. */
public interface StudentNumberGenerator {
    /**
     * Performs the next operation.
     * @param transaction the transaction
     * @param majorCode the major code
     * @param enrollmentYear the enrollment year
     * @param classNumber the class number
     * @return the operation result
     */
    String next(TransactionContext transaction, String majorCode,
                int enrollmentYear, int classNumber);
}
