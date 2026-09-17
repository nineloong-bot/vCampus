package edu.seu.vcampus.server.student.numbering;

import edu.seu.vcampus.server.persistence.TransactionContext;

/** Allocates the next student number for one major/year/class sequence. */
public interface StudentNumberGenerator {
    String next(TransactionContext transaction, String majorCode,
                int enrollmentYear, int classNumber);

    /** Starts a class number sequence over before a full freshman-cohort rebalance. */
    default void reset(TransactionContext transaction, String majorCode,
            int enrollmentYear, int classNumber) { throw new UnsupportedOperationException(); }
}
