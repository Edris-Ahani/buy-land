package com.acme.platform.auth.application;

import com.acme.platform.auth.domain.UserType;
import java.time.Instant;
import java.util.UUID;

public interface AccessTokens {
    String issue(UUID userId, UUID sessionId, UserType type, Instant issuedAt, Instant expiresAt);
}
