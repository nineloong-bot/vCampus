package edu.seu.vcampus.server.student.domain;

/** Persistent state of one campus-card or class-number sequence. */
public record NumberSequence(String sequenceKey, int currentValue,
                             int maxValue, long rowVersion) {
    public NumberSequence {
        if (sequenceKey == null || sequenceKey.isBlank()) {
            throw new IllegalArgumentException("sequenceKey is required");
        }
        if (currentValue < 0 || maxValue < 1 || currentValue > maxValue) {
            throw new IllegalArgumentException("Invalid sequence range");
        }
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
    }

    public NumberSequence incremented() {
        return new NumberSequence(sequenceKey, currentValue + 1, maxValue, rowVersion + 1);
    }
}
