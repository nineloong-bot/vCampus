package edu.seu.vcampus.common.governance;

import java.io.Serializable;

/**
 * Deactivates one dedicated module-administrator account using its account row version.
 * The legacy field name {@code expectedAssignmentVersion} is retained for compatibility.
 */
/**
 * Carries immutable remove module administrator command data.
 * @param moduleCode the module code
 * @param userId the user identifier
 * @param expectedAssignmentVersion the expected assignment version
 */
public record RemoveModuleAdministratorCommand(
        String moduleCode, String userId, long expectedAssignmentVersion) implements Serializable { }
