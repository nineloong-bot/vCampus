package edu.seu.vcampus.server.user.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
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
        byte[] hash = derive(password, salt, ITERATIONS);
        return new PasswordHash(
                Base64.getEncoder().encodeToString(hash),
                Base64.getEncoder().encodeToString(salt),
                ITERATIONS);
    }

    boolean verify(char[] password, String encodedHash, String encodedSalt, int iterations) {
        byte[] expected = Base64.getDecoder().decode(encodedHash);
        byte[] salt = Base64.getDecoder().decode(encodedSalt);
        byte[] actual = derive(password, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec specification = new PBEKeySpec(
                password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            specification.clearPassword();
        }
    }
}

record PasswordHash(String hash, String salt, int iterations) {
}
