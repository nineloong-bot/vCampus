package edu.seu.vcampus.server.user.service;

/** Account identity created inside a caller-owned admission transaction. */
public record ProvisionedUserAccount(String userId, String loginId, boolean mustChangePassword) { }
