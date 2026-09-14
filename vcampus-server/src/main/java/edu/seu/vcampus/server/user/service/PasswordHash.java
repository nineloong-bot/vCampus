package edu.seu.vcampus.server.user.service;

/** Salted hash value with PBKDF2 parameters. */
public record PasswordHash(String hash, String salt, int iterations) {
}
