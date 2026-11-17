package com.acme.platform.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class Pbkdf2PasswordsTest {
    @Test
    void hashesAreSaltedAndPasswordsAreNeverTruncatedOrNormalized() {
        var passwords = new Pbkdf2Passwords();
        String password = "A sufficiently long test password " + "x".repeat(70);
        String hash = passwords.hash(password);
        assertThat(hash).doesNotContain(password).isNotEqualTo(passwords.hash(password));
        assertThat(passwords.matches(password, hash)).isTrue();
        assertThat(passwords.matches(password + "y", hash)).isFalse();
        assertThat(passwords.matches("incorrect", hash)).isFalse();
        assertThat(passwords.matches(password, "unrecognized-hash")).isFalse();
    }
}
