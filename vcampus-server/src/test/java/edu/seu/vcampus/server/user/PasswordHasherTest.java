package edu.seu.vcampus.server.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {
    @Test
    void acceptsOriginalPasswordAndRejectsAnother() {
        PasswordHasher hasher = new PasswordHasher();
        PasswordHasher.PasswordHash stored = hasher.hash("Admin1234".toCharArray());

        assertThat(hasher.matches("Admin1234".toCharArray(), stored.hash(),
                stored.salt(), stored.iterations())).isTrue();
        assertThat(hasher.matches("Wrong1234".toCharArray(), stored.hash(),
                stored.salt(), stored.iterations())).isFalse();
    }
}
