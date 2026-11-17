package com.acme.platform.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(UUID id, UUID userId, Instant expiresAt, boolean revoked) {
    public boolean activeAt(Instant instant) { return !revoked && expiresAt.isAfter(instant); }
}
