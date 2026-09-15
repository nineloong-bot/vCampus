package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Student category encoded in the second campus-card digit. */
public enum StudentType implements Serializable {
    /** Represents undergraduate. */ UNDERGRADUATE('1'),
    /** Represents master. */ MASTER('2'),
    /** Represents doctorate. */ DOCTORATE('3');

    private final char digit;

    StudentType(char digit) {
        this.digit = digit;
    }

    /**
     * Returns the digit result.
     * @return the computed result
     */
    public char digit() {
        return digit;
    }
}
