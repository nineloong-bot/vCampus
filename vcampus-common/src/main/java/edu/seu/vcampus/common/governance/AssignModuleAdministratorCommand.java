package edu.seu.vcampus.common.governance;

import java.io.Serializable;

/**
 * Changes an existing dedicated module-administrator account to the requested module role.
 * The legacy field name {@code expectedModuleVersion} carries the target account row version.
 */
/**
 * Carries immutable assign module administrator command data.
 * @param moduleCode the module code
 * @param userId the user identifier
 * @param expectedModuleVersion the expected module version
 */
public record AssignModuleAdministratorCommand(
        String moduleCode, String userId, long expectedModuleVersion) implements Serializable { }
