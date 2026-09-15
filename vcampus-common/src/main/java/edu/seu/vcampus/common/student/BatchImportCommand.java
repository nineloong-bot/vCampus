package edu.seu.vcampus.common.student;

import java.io.Serializable;
import java.util.List;

/** Command to batch-create students and distribute them across classes. */
/**
 * Carries immutable batch import command data.
 * @param majorId the major identifier
 * @param classIds the class identifiers
 * @param entries the entries
 */
public record BatchImportCommand(
        String majorId,
        List<String> classIds,
        List<BatchStudentEntry> entries) implements Serializable { }
