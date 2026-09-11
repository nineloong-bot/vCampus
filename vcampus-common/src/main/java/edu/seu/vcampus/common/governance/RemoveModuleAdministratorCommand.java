package edu.seu.vcampus.common.governance;

import java.io.Serializable;

/**
 * Deactivates one dedicated module-administrator account using its account row version.
 * The legacy field name {@code expectedAssignmentVersion} is retained for compatibility.
 */
public record RemoveModuleAdministratorCommand(
        String moduleCode, String userId, long expectedAssignmentVersion) implements Serializable { }
