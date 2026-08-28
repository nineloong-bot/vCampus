package edu.seu.vcampus.common.protocol;

import java.io.Serializable;

/** Singleton response body for commands without result data. */
public enum EmptyResponse implements Serializable {
    INSTANCE
}
