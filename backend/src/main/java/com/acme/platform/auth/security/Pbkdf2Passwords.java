package com.acme.platform.auth.security;

import com.acme.platform.auth.application.PasswordHasher;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;
import org.springframework.stereotype.Component;

@Component
public class Pbkdf2Passwords implements PasswordHasher {
    private static final String PREFIX = "{pbkdf2-sha256-600000}";
    private final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 16, 600_000,
            SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

    @Override
    public String hash(String password) { return PREFIX + encoder.encode(password); }

    @Override
    public boolean matches(String password, String hash) {
        return hash != null && hash.startsWith(PREFIX) && encoder.matches(password, hash.substring(PREFIX.length()));
    }
}
