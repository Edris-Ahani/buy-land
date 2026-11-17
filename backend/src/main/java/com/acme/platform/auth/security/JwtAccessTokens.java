package com.acme.platform.auth.security;

import com.acme.platform.auth.application.AccessTokens;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class JwtAccessTokens implements AccessTokens {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;

    public JwtAccessTokens(JwtEncoder encoder, @Value("${platform.auth.issuer}") String issuer,
            @Value("${platform.auth.audience}") String audience) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    public String issue(UUID userId, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(userId.toString())
                .id(UUID.randomUUID().toString()).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("sid", sessionId.toString()).claim("token_use", "access").claim("scope", "user").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }
}
