package edu.seu.vcampus.server.user.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Creates salted PBKDF2-HMAC-SHA256 password hashes without retaining plaintext. */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    /** Creates a password hasher using secure per-password random salts. */
    public PasswordHasher() {
    }

    PasswordHash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        PBEKeySpec specification = new PBEKeySpec(
                password, salt, ITERATIONS, HASH_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(specification).getEncoded();
            return new PasswordHash(
                    Base64.getEncoder().encodeToString(hash),
                    Base64.getEncoder().encodeToString(salt),
                    ITERATIONS);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            specification.clearPassword();
        }
    }
}

record PasswordHash(String hash, String salt, int iterations) {
}
