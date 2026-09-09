package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Command to batch-create students and distribute them across classes. */
public record BatchImportCommand(
        String majorId,
        List<String> classIds,
        List<BatchStudentEntry> entries) implements Serializable { }
