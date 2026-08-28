package edu.seu.vcampus.common.user;

import java.io.Serializable;

/** Lifecycle state of a user account. */
public enum AccountStatus implements Serializable {
    PENDING, ACTIVE, DISABLED, CANCELLED
}
