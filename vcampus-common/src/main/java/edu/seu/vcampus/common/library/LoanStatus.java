package edu.seu.vcampus.common.library;

import java.io.Serializable;

/** Lifecycle status of a library loan. */
public enum LoanStatus implements Serializable {
    ACTIVE, RETURNED, OVERDUE, LOST
}
