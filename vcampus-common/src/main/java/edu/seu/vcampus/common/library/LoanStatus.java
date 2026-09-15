package edu.seu.vcampus.common.library;

import java.io.Serializable;

/** Lifecycle status of a library loan. */
public enum LoanStatus implements Serializable {
    /** Represents active. */ ACTIVE, /** Represents returned. */ RETURNED, /** Represents overdue. */ OVERDUE, /** Represents lost. */ LOST
}
