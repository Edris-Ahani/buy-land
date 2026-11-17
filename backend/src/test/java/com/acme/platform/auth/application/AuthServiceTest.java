package com.acme.platform.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acme.platform.auth.domain.AuthSession;
import com.acme.platform.auth.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthServiceTest {
    private final AuthStore store = mock(AuthStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final AccessTokens access = mock(AccessTokens.class);
    private final Instant now = Instant.parse("2026-11-17T10:00:00Z");
    private final UserAccount user = new UserAccount(UUID.randomUUID(), "Test User", "+989120000001", "encoded-password", true, now);
    private AuthService service;

    @BeforeEach
    void prepare() {
        given(passwords.hash(anyString())).willReturn("encoded-password");
        given(access.issue(any(), any(), any(), any())).willReturn("signed-access-token");
        service = new AuthService(store, passwords, access, Clock.fixed(now, ZoneOffset.UTC), Duration.ofMinutes(15), Duration.ofDays(30));
    }

    @Test
    void registrationHashesThePasswordAndPersistsOnlyTheRefreshHash() {
        given(store.insertUser(any())).willReturn(true);
        var tokens = service.register("Test User", "09120000001", "ExamplePassword42!");
        var userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(store).insertUser(userCaptor.capture());
        assertThat(userCaptor.getValue().passwordHash()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().phoneNumber()).isEqualTo("+989120000001");
        var hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).insertRefreshToken(hashCaptor.capture(), any());
        assertThat(hashCaptor.getValue()).matches("[0-9a-f]{64}").isNotEqualTo(tokens.refreshToken());
        assertThat(tokens.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(tokens.expiresIn()).isEqualTo(900);
        assertThat(tokens.refreshExpiresIn()).isEqualTo(2_592_000);
        assertThat(tokens.toString()).doesNotContain(tokens.accessToken(), tokens.refreshToken());
    }

    @Test
    void duplicatePhoneDoesNotStartASession() {
        given(store.insertUser(any())).willReturn(false);
        assertThatThrownBy(() -> service.register("Test User", "09120000001", "ExamplePassword42!"))
                .isInstanceOf(AuthService.PhoneAlreadyRegistered.class);
        verify(store, never()).insertSession(any());
    }

    @Test
    void loginUsesTheSameGenericFailureForUnknownUsersAndWrongPasswords() {
        given(store.findUserByPhone(anyString())).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("09120000001", "ExamplePassword42!"))
                .isInstanceOf(AuthService.InvalidCredentials.class);
        verify(passwords).matches("ExamplePassword42!", "encoded-password");
        given(store.findUserByPhone(anyString())).willReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login("09120000001", "ExamplePassword42!"))
                .isInstanceOf(AuthService.InvalidCredentials.class);
        verify(store, never()).insertSession(any());
    }

    @Test
    void refreshReplayRevokesTheWholeSession() {
        var session = new AuthSession(UUID.randomUUID(), user.id(), now.plusSeconds(600), false);
        given(store.lockSessionByRefreshToken(anyString())).willReturn(Optional.of(session));
        given(store.refreshTokenConsumed(anyString())).willReturn(true);
        assertThatThrownBy(() -> service.refresh("a".repeat(43))).isInstanceOf(AuthService.InvalidCredentials.class);
        verify(store).revokeSession(session.id());
        verify(store, never()).insertRefreshToken(anyString(), any());
    }

    @Test
    void refreshRotatesAndCapsAccessExpiryAtTheAbsoluteSessionExpiry() {
        var session = new AuthSession(UUID.randomUUID(), user.id(), now.plusSeconds(60), false);
        given(store.lockSessionByRefreshToken(anyString())).willReturn(Optional.of(session));
        given(store.findUser(user.id())).willReturn(Optional.of(user));
        var tokens = service.refresh("a".repeat(43));
        verify(store).consumeRefreshToken(anyString());
        verify(store).insertRefreshToken(anyString(), org.mockito.ArgumentMatchers.eq(session.id()));
        assertThat(tokens.refreshToken()).isNotEqualTo("a".repeat(43));
        assertThat(tokens.expiresIn()).isEqualTo(60);
        assertThat(tokens.refreshExpiresIn()).isEqualTo(60);
    }

    @Test
    void expiredAndDisabledSessionsCannotRefresh() {
        var expired = new AuthSession(UUID.randomUUID(), user.id(), now, false);
        given(store.lockSessionByRefreshToken(anyString())).willReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.refresh("a".repeat(43))).isInstanceOf(AuthService.InvalidCredentials.class);
        verify(store).revokeSession(expired.id());
        var active = new AuthSession(UUID.randomUUID(), user.id(), now.plusSeconds(60), false);
        given(store.lockSessionByRefreshToken(anyString())).willReturn(Optional.of(active));
        given(store.findUser(user.id())).willReturn(Optional.of(new UserAccount(user.id(), user.fullName(), user.phoneNumber(), user.passwordHash(), false, now)));
        assertThatThrownBy(() -> service.refresh("a".repeat(43))).isInstanceOf(AuthService.InvalidCredentials.class);
        verify(store).revokeSession(active.id());
    }
}
