package com.acme.platform.auth.application;

import com.acme.platform.auth.domain.AuthSession;
import com.acme.platform.auth.domain.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuthStore {
    boolean insertUser(UserAccount user);
    boolean insertBootstrapUserIfAbsent(UserAccount user);
    Optional<UserAccount> findUserByPhone(String phoneNumber);
    Optional<UserAccount> findUser(UUID id);
    void insertSession(AuthSession session);
    void insertRefreshToken(String hash, UUID sessionId);
    Optional<AuthSession> lockSessionByRefreshToken(String hash);
    boolean refreshTokenConsumed(String hash);
    void consumeRefreshToken(String hash);
    void revokeSession(UUID sessionId);
    void revokeUserSessions(UUID userId);
    boolean sessionActive(UUID sessionId, UUID userId, Instant now);
}
