package edu.seu.vcampus.server.user.service;

import edu.seu.vcampus.server.persistence.TransactionContext;

/** User-module contract consumed by student admission; implementations must not open a transaction. */
@FunctionalInterface
public interface UserAccountProvisioningPort {
    ProvisionedUserAccount createStudentAccount(TransactionContext transaction,
            String campusCardNumber, char[] initialPassword) throws Exception;
}
