package edu.seu.vcampus.common.student;

import java.io.Serializable;

/** Student category encoded in the second campus-card digit. */
public enum StudentType implements Serializable {
    UNDERGRADUATE('1'),
    MASTER('2'),
    DOCTORATE('3');

    private final char digit;

    StudentType(char digit) {
        this.digit = digit;
    }

    public char digit() {
        return digit;
    }
}
