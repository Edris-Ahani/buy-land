package com.acme.platform.auth.security;

import com.acme.platform.auth.api.SessionVerifier;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {
    @Bean
    SecretKey jwtSigningKey(@Value("${platform.auth.jwt-secret}") String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The JWT secret must be Base64 encoded");
        }
        if (bytes.length < 32) throw new IllegalArgumentException("The JWT secret must contain at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, SessionVerifier sessions, Clock clock,
            @Value("${platform.auth.issuer}") String issuer, @Value("${platform.auth.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        OAuth2TokenValidator<Jwt> session = jwt -> {
            try {
                boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                        && !jwt.getIssuedAt().isAfter(clock.instant()) && jwt.getAudience().contains(audience)
                        && "access".equals(jwt.getClaimAsString("token_use"))
                        && sessions.isActive(UUID.fromString(jwt.getClaimAsString("sid")), UUID.fromString(jwt.getSubject()));
                if (valid) return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException exception) {
                // Malformed claims are authentication failures, never application errors.
            } catch (RuntimeException exception) {
                // The security filter must not expose persistence failures or their exception chains.
                throw new JwtException("Authentication validation is unavailable");
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The access token is invalid", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(issuer), session));
        return decoder;
    }
}
