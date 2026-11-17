package com.acme.platform.auth.api;

import java.util.UUID;

public interface SessionVerifier {
    boolean isActive(UUID sessionId, UUID userId);
}
