package edu.seu.vcampus.common.library;

import java.io.Serializable;

/** Physical copy status in the library catalog. */
public enum CopyStatus implements Serializable {
    AVAILABLE, BORROWED, LOST, DAMAGED
}
