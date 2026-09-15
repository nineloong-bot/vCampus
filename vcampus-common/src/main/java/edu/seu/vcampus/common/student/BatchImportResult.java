package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Result of a batch student import operation. */
/**
 * Carries immutable batch import result data.
 * @param totalCreated the total created
 * @param totalFailed the total failed
 * @param errors the errors
 */
public record BatchImportResult(
        int totalCreated,
        int totalFailed,
        List<String> errors) implements Serializable { }
