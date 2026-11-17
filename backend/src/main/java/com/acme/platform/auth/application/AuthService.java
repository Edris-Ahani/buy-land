package com.acme.platform.auth.application;

import com.acme.platform.auth.api.SessionVerifier;
import com.acme.platform.auth.domain.AuthInput;
import com.acme.platform.auth.domain.AuthSession;
import com.acme.platform.auth.domain.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService implements SessionVerifier {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthStore store;
    private final PasswordHasher passwords;
    private final AccessTokens accessTokens;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final String dummyPasswordHash;

    public AuthService(AuthStore store, PasswordHasher passwords, AccessTokens accessTokens, Clock clock,
            @Value("${platform.auth.access-ttl:PT15M}") Duration accessTtl,
            @Value("${platform.auth.refresh-ttl:P30D}") Duration refreshTtl) {
        if (accessTtl.isNegative() || accessTtl.isZero() || accessTtl.compareTo(Duration.ofHours(1)) > 0
                || refreshTtl.compareTo(accessTtl) <= 0 || refreshTtl.compareTo(Duration.ofDays(90)) > 0) {
            throw new IllegalArgumentException("Invalid authentication lifetimes");
        }
        this.store = store;
        this.passwords = passwords;
        this.accessTokens = accessTokens;
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.dummyPasswordHash = passwords.hash(UUID.randomUUID().toString());
    }

    @Transactional
    public Tokens register(String fullName, String phoneNumber, String password) {
        String name = AuthInput.fullName(fullName);
        String phone = AuthInput.phoneNumber(phoneNumber);
        String hash = passwords.hash(AuthInput.password(password));
        UserAccount user = new UserAccount(UUID.randomUUID(), name, phone, hash, true, clock.instant());
        if (!store.insertUser(user)) throw new PhoneAlreadyRegistered();
        return newSession(user);
    }

    @Transactional
    public Tokens login(String phoneNumber, String password) {
        String phone = AuthInput.phoneNumber(phoneNumber);
        AuthInput.password(password);
        UserAccount user = store.findUserByPhone(phone).orElse(null);
        boolean passwordMatches = passwords.matches(password, user == null ? dummyPasswordHash : user.passwordHash());
        if (user == null || !passwordMatches || !user.enabled()) throw new InvalidCredentials();
        return newSession(user);
    }

    // Replay revocation must commit even though the refresh request returns an authentication error.
    @Transactional(noRollbackFor = InvalidCredentials.class)
    public Tokens refresh(String refreshToken) {
        String hash = refreshHash(refreshToken);
        AuthSession session = store.lockSessionByRefreshToken(hash).orElseThrow(InvalidCredentials::new);
        if (!session.activeAt(clock.instant()) || store.refreshTokenConsumed(hash)) {
            store.revokeSession(session.id());
            throw new InvalidCredentials();
        }
        UserAccount user = store.findUser(session.userId()).orElseThrow(InvalidCredentials::new);
        if (!user.enabled()) {
            store.revokeSession(session.id());
            throw new InvalidCredentials();
        }
        store.consumeRefreshToken(hash);
        return issueTokens(user, session);
    }

    @Transactional
    public void logout(String refreshToken) {
        store.lockSessionByRefreshToken(refreshHash(refreshToken)).ifPresent(session -> store.revokeSession(session.id()));
    }

    @Transactional
    public void logoutAll(UUID userId) { store.revokeUserSessions(userId); }

    @Transactional(readOnly = true)
    public UserAccount currentUser(UUID userId) {
        return store.findUser(userId).filter(UserAccount::enabled).orElseThrow(InvalidCredentials::new);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, UUID userId) {
        return store.sessionActive(sessionId, userId, clock.instant());
    }

    private Tokens newSession(UserAccount user) {
        AuthSession session = new AuthSession(UUID.randomUUID(), user.id(), clock.instant().plus(refreshTtl), false);
        store.insertSession(session);
        return issueTokens(user, session);
    }

    private Tokens issueTokens(UserAccount user, AuthSession session) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(accessTtl);
        if (expiresAt.isAfter(session.expiresAt())) expiresAt = session.expiresAt();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.insertRefreshToken(refreshHash(refreshToken), session.id());
        return new Tokens(accessTokens.issue(user.id(), session.id(), now, expiresAt), refreshToken,
                Duration.between(now, expiresAt).toSeconds(), Duration.between(now, session.expiresAt()).toSeconds(), user);
    }

    private static String refreshHash(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw new InvalidCredentials();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    public record Tokens(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn, UserAccount user) {
        @Override
        public String toString() { return "Tokens[REDACTED]"; }
    }

    public static final class InvalidCredentials extends RuntimeException {}
    public static final class PhoneAlreadyRegistered extends RuntimeException {}
}
