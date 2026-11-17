package com.acme.platform.auth.persistence;

import com.acme.platform.auth.application.AuthStore;
import com.acme.platform.auth.domain.AuthSession;
import com.acme.platform.auth.domain.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthStore implements AuthStore {
    private final JdbcClient jdbc;

    public JdbcAuthStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean insertUser(UserAccount user) {
        return jdbc.sql("""
                INSERT INTO app_users (id, full_name, phone_number, password_hash, enabled, created_at)
                VALUES (:id, :name, :phone, :hash, :enabled, :created)
                ON CONFLICT (phone_number) DO NOTHING
                """)
                .param("id", user.id()).param("name", user.fullName()).param("phone", user.phoneNumber())
                .param("hash", user.passwordHash()).param("enabled", user.enabled())
                .param("created", Timestamp.from(user.createdAt())).update() == 1;
    }

    @Override
    public Optional<UserAccount> findUserByPhone(String phoneNumber) {
        return jdbc.sql("SELECT * FROM app_users WHERE phone_number = :phone").param("phone", phoneNumber)
                .query((row, index) -> user(row)).optional();
    }

    @Override
    public Optional<UserAccount> findUser(UUID id) {
        return jdbc.sql("SELECT * FROM app_users WHERE id = :id").param("id", id)
                .query((row, index) -> user(row)).optional();
    }

    @Override
    public void insertSession(AuthSession session) {
        jdbc.sql("INSERT INTO auth_sessions (id, user_id, expires_at, revoked) VALUES (:id, :user, :expiry, false)")
                .param("id", session.id()).param("user", session.userId())
                .param("expiry", Timestamp.from(session.expiresAt())).update();
    }

    @Override
    public void insertRefreshToken(String hash, UUID sessionId) {
        jdbc.sql("INSERT INTO auth_refresh_tokens (token_hash, session_id) VALUES (:hash, :session)")
                .param("hash", hash).param("session", sessionId).update();
    }

    @Override
    public Optional<AuthSession> lockSessionByRefreshToken(String hash) {
        return jdbc.sql("""
                SELECT s.* FROM auth_sessions s JOIN auth_refresh_tokens t ON t.session_id = s.id
                WHERE t.token_hash = :hash FOR UPDATE OF s
                """).param("hash", hash).query((row, index) -> new AuthSession(row.getObject("id", UUID.class),
                        row.getObject("user_id", UUID.class), row.getTimestamp("expires_at").toInstant(), row.getBoolean("revoked")))
                .optional();
    }

    @Override
    public boolean refreshTokenConsumed(String hash) {
        return jdbc.sql("SELECT consumed FROM auth_refresh_tokens WHERE token_hash = :hash").param("hash", hash)
                .query(Boolean.class).single();
    }

    @Override
    public void consumeRefreshToken(String hash) {
        jdbc.sql("UPDATE auth_refresh_tokens SET consumed = true WHERE token_hash = :hash").param("hash", hash).update();
    }

    @Override
    public void revokeSession(UUID sessionId) {
        jdbc.sql("UPDATE auth_sessions SET revoked = true WHERE id = :id").param("id", sessionId).update();
    }

    @Override
    public void revokeUserSessions(UUID userId) {
        jdbc.sql("UPDATE auth_sessions SET revoked = true WHERE user_id = :user AND revoked = false").param("user", userId).update();
    }

    @Override
    public boolean sessionActive(UUID sessionId, UUID userId, Instant now) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM auth_sessions s JOIN app_users u ON u.id = s.user_id
                WHERE s.id = :session AND s.user_id = :user AND s.revoked = false AND s.expires_at > :now AND u.enabled = true)
                """).param("session", sessionId).param("user", userId).param("now", Timestamp.from(now))
                .query(Boolean.class).single();
    }

    private static UserAccount user(ResultSet row) throws SQLException {
        return new UserAccount(row.getObject("id", UUID.class), row.getString("full_name"), row.getString("phone_number"),
                row.getString("password_hash"), row.getBoolean("enabled"), row.getTimestamp("created_at").toInstant());
    }
}
