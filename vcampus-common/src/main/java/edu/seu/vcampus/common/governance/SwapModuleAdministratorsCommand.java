package edu.seu.vcampus.common.governance;

import java.io.Serializable;

/** Atomically exchanges two dedicated administrator roles using account row versions. */
/**
 * Carries immutable swap module administrators command data.
 * @param firstModuleCode the first module code
 * @param firstUserId the first user identifier
 * @param firstExpectedVersion the first expected version
 * @param secondModuleCode the second module code
 * @param secondUserId the second user identifier
 * @param secondExpectedVersion the second expected version
 */
public record SwapModuleAdministratorsCommand(
        String firstModuleCode, String firstUserId, long firstExpectedVersion,
        String secondModuleCode, String secondUserId, long secondExpectedVersion)
        implements Serializable { }
