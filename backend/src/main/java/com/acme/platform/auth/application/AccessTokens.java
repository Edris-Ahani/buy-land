package com.acme.platform.auth.application;

import java.time.Instant;
import java.util.UUID;

public interface AccessTokens {
    String issue(UUID userId, UUID sessionId, Instant issuedAt, Instant expiresAt);
}
