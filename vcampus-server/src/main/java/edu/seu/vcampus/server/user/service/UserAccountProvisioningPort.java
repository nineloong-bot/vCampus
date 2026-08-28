package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.persistence.TransactionContext;

/** Internal server port for creating a student account in the caller's transaction. */
public interface UserAccountProvisioningPort {
    /**
     * Creates a student account using an already-open transaction and clears the
     * supplied password array when the operation finishes.
     */
    ProvisionedUserAccount createStudentAccount(
            TransactionContext transaction,
            String campusCardNumber,
            char[] initialPassword);
}
