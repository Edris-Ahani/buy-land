package com.acme.platform.auth.application;

import com.acme.platform.auth.domain.AuthInput;
import com.acme.platform.auth.domain.UserAccount;
import com.acme.platform.auth.domain.UserType;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAccountSeeder {
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private final AuthStore store;
    private final PasswordHasher passwords;
    private final Clock clock;

    public AdminAccountSeeder(AuthStore store, PasswordHasher passwords, Clock clock) {
        this.store = store;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public Result ensureAdministrator(String fullName, String phoneNumber, String password) {
        UserAccount existing = store.findUser(ADMIN_ID).orElse(null);
        if (existing != null) return existingAdministrator(existing);

        String name = AuthInput.fullName(fullName);
        String phone = AuthInput.phoneNumber(phoneNumber);
        String hash = passwords.hash(AuthInput.password(password));
        UserAccount administrator = new UserAccount(ADMIN_ID, name, phone, hash, UserType.ADMIN, true, clock.instant());
        if (store.insertBootstrapUserIfAbsent(administrator)) return Result.CREATED;

        // A concurrent startup may have inserted the same account while this transaction waited.
        return store.findUser(ADMIN_ID).map(AdminAccountSeeder::existingAdministrator)
                .orElseThrow(() -> new IllegalStateException("Bootstrap administrator phone is already assigned to another account"));
    }

    private static Result existingAdministrator(UserAccount account) {
        if (account.type() != UserType.ADMIN) {
            throw new IllegalStateException("Bootstrap administrator identifier belongs to a non-administrator account");
        }
        return Result.SKIPPED;
    }

    public enum Result { CREATED, SKIPPED }
}
