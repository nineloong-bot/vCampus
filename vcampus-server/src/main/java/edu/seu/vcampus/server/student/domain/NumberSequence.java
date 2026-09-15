package edu.seu.vcampus.server.student.domain;

/** Persistent state of one campus-card or class-number sequence. */
public record NumberSequence(String sequenceKey, int currentValue,
                             int maxValue, long rowVersion) {
    /**
     * Creates a number sequence with its required collaborators.
     * @param sequenceKey the sequence key
     * @param currentValue the current value
     * @param maxValue the max value
     * @param rowVersion the row version
     */
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

    /**
     * Performs the incremented operation.
     * @return the operation result
     */
    public NumberSequence incremented() {
        return new NumberSequence(sequenceKey, currentValue + 1, maxValue, rowVersion + 1);
    }
}
