package edu.seu.vcampus.server.user.service;

/** Applies the shared server-side password policy. */
final class PasswordPolicy {
    private PasswordPolicy() { }

    static void validate(char[] password) {
        if (password == null || password.length < 8 || password.length > 64) {
            throw new IllegalArgumentException("AUTH_PASSWORD_POLICY_VIOLATION");
        }
        boolean letter = false;
        boolean digit = false;
        for (char character : password) {
            letter |= Character.isLetter(character);
            digit |= Character.isDigit(character);
        }
        if (!letter || !digit) {
            throw new IllegalArgumentException("AUTH_PASSWORD_POLICY_VIOLATION");
        }
    }
}
