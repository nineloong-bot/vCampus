package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Result of a batch student import operation. */
public record BatchImportResult(
        int totalCreated,
        int totalFailed,
        List<String> errors) implements Serializable { }
