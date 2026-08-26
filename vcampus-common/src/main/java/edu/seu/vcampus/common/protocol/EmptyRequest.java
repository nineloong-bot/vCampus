package edu.seu.vcampus.common.protocol;

import java.io.Serializable;

/** Singleton request body for commands without parameters. */
public enum EmptyRequest implements Serializable {
    INSTANCE
}
