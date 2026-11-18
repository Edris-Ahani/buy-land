package com.acme.platform.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.acme.platform.auth.domain.AuthInput;
import com.acme.platform.auth.domain.UserAccount;
import com.acme.platform.auth.domain.UserType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminAccountSeederTest {
    private final AuthStore store = mock(AuthStore.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final Instant now = Instant.parse("2026-11-18T09:00:00Z");
    private final AdminAccountSeeder seeder = new AdminAccountSeeder(store, passwords, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void createsAnEnabledAdministratorWithTheFixedIdentifierAndAHashedPassword() {
        given(passwords.hash(anyString())).willReturn("encoded-seed-password");
        given(store.insertBootstrapUserIfAbsent(any())).willReturn(true);
        assertThat(seeder.ensureAdministrator("Bootstrap Administrator", "09129999999", "SyntheticPassword42!"))
                .isEqualTo(AdminAccountSeeder.Result.CREATED);
        var account = ArgumentCaptor.forClass(UserAccount.class);
        verify(store).insertBootstrapUserIfAbsent(account.capture());
        assertThat(account.getValue().id()).isEqualTo(AdminAccountSeeder.ADMIN_ID);
        assertThat(account.getValue().type()).isEqualTo(UserType.ADMIN);
        assertThat(account.getValue().enabled()).isTrue();
        assertThat(account.getValue().phoneNumber()).isEqualTo("+989129999999");
        assertThat(account.getValue().passwordHash()).isEqualTo("encoded-seed-password");
        verify(store, never()).insertSession(any());
    }

    @Test
    void skipsAnExistingAdministratorWithoutRequiringCredentialsOrResettingDisabledState() {
        given(store.findUser(AdminAccountSeeder.ADMIN_ID)).willReturn(Optional.of(account(UserType.ADMIN, false)));
        assertThat(seeder.ensureAdministrator("", "", "")).isEqualTo(AdminAccountSeeder.Result.SKIPPED);
        verifyNoInteractions(passwords);
        verify(store, never()).insertBootstrapUserIfAbsent(any());
    }

    @Test
    void neverPromotesAnExistingUserAtTheReservedIdentifier() {
        given(store.findUser(AdminAccountSeeder.ADMIN_ID)).willReturn(Optional.of(account(UserType.USER, true)));
        assertThatThrownBy(() -> seeder.ensureAdministrator("", "", "")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(passwords);
        verify(store, never()).insertBootstrapUserIfAbsent(any());
    }

    @Test
    void concurrentInsertionOfTheSameAdministratorIsSkipped() {
        given(store.findUser(AdminAccountSeeder.ADMIN_ID)).willReturn(Optional.empty(), Optional.of(account(UserType.ADMIN, true)));
        given(passwords.hash(anyString())).willReturn("encoded-seed-password");
        assertThat(seeder.ensureAdministrator("Bootstrap Administrator", "09129999999", "SyntheticPassword42!"))
                .isEqualTo(AdminAccountSeeder.Result.SKIPPED);
    }

    @Test
    void conflictingPhoneOrMissingCredentialsFailWithoutCreatingASession() {
        given(passwords.hash(anyString())).willReturn("encoded-seed-password");
        assertThatThrownBy(() -> seeder.ensureAdministrator("Bootstrap Administrator", "09129999999", "SyntheticPassword42!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeder.ensureAdministrator("", "", "")).isInstanceOf(AuthInput.InvalidField.class);
        verify(store, never()).insertSession(any());
    }

    private UserAccount account(UserType type, boolean enabled) {
        return new UserAccount(AdminAccountSeeder.ADMIN_ID, "Existing Administrator", "+989129999999", "existing-hash", type, enabled, now);
    }
}
