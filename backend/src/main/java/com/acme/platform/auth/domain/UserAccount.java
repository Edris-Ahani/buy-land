package com.acme.platform.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(UUID id, String fullName, String phoneNumber, String passwordHash, UserType type,
        boolean enabled, Instant createdAt) {
    public UserAccount {
        java.util.Objects.requireNonNull(type, "User type is required");
    }
    @Override
    public String toString() { return "UserAccount[id=" + id + "]"; }
}
