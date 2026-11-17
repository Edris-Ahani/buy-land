package com.acme.platform.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtConfigurationTest {
    private final JwtConfiguration configuration = new JwtConfiguration();

    @Test
    void acceptsATrailingNewlineFromASecretFile() {
        String encoded = Base64.getEncoder().encodeToString(new byte[48]);
        assertThat(configuration.jwtSigningKey(encoded + "\n").getEncoded()).hasSize(48);
    }

    @Test
    void rejectsMalformedAndUndersizedSigningSecretsWithoutEchoingThem() {
        assertThatThrownBy(() -> configuration.jwtSigningKey("invalid-secret!"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The JWT secret must be Base64 encoded");
        assertThatThrownBy(() -> configuration.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("The JWT secret must contain at least 32 random bytes");
    }
}
