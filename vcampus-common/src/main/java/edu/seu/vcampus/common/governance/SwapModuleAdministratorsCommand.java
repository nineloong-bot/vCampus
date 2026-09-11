package edu.seu.vcampus.common.governance;

import java.io.Serializable;

/** Atomically exchanges two dedicated administrator roles using account row versions. */
public record SwapModuleAdministratorsCommand(
        String firstModuleCode, String firstUserId, long firstExpectedVersion,
        String secondModuleCode, String secondUserId, long secondExpectedVersion)
        implements Serializable { }
