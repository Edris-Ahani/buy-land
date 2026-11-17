package com.acme.platform.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id, String fullName, String phoneNumber, String passwordHash, boolean enabled, Instant createdAt) {
    @Override
    public String toString() { return "UserAccount[id=" + id + "]"; }
}
