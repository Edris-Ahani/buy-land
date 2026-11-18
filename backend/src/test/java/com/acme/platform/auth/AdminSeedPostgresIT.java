package com.acme.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.acme.platform.auth.application.AdminAccountSeeder;
import com.acme.platform.auth.application.AuthService;
import com.acme.platform.auth.application.AuthStore;
import com.acme.platform.auth.domain.UserType;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthPostgresIT.Containers.class)
class AdminSeedPostgresIT {
    @Autowired private AdminAccountSeeder seeder;
    @Autowired private AuthService auth;
    @Autowired private AuthStore store;
    @Autowired private JdbcClient jdbc;
    @Autowired private JwtDecoder decoder;
    @Autowired private MockMvc mvc;
    @Value("${platform.auth.bootstrap-admin.password}") private String initialPassword;

    @Test
    void startupCreatesTheAdministratorAfterMigrationsAndItsPasswordAuthenticates() {
        var account = auth.currentUser(AdminAccountSeeder.ADMIN_ID);
        assertThat(account.type()).isEqualTo(UserType.ADMIN);
        assertThat(account.passwordHash()).startsWith("{pbkdf2-sha256-600000}").doesNotContain(initialPassword);
        var tokens = auth.login("09129999999", initialPassword);
        assertThat(decoder.decode(tokens.accessToken()).getClaimAsString("type")).isEqualTo("admin");
        assertThat(seeder.ensureAdministrator("Changed Administrator", "09129999998", "DifferentSyntheticPassword42!"))
                .isEqualTo(AdminAccountSeeder.Result.SKIPPED);
        assertThat(auth.currentUser(AdminAccountSeeder.ADMIN_ID)).isEqualTo(account);
        assertThat(auth.login("09129999999", initialPassword).user().id()).isEqualTo(AdminAccountSeeder.ADMIN_ID);
    }

    @Test
    void repeatedAndConcurrentSeedChecksRemainIdempotent() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<AdminAccountSeeder.Result> seed = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return seeder.ensureAdministrator("", "", "");
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(seed, seed), 20, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsOnly(AdminAccountSeeder.Result.SKIPPED);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM app_users WHERE id = :id")
                .param("id", AdminAccountSeeder.ADMIN_ID).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void publicRegistrationStillCannotCreateAnAdministrator() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Unauthorized Administrator\",\"phoneNumber\":\"09129999997\","
                        + "\"password\":\"SyntheticPassword42!\",\"type\":\"admin\"}")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThatThrownBy(() -> auth.login("09129999997", "SyntheticPassword42!"))
                .isInstanceOf(AuthService.InvalidCredentials.class);
    }

    @Test
    void concurrentFirstStartupCreatesOneAccountAndPhoneConflictsNeverPromoteAnotherUser() throws Exception {
        var original = auth.currentUser(AdminAccountSeeder.ADMIN_ID);
        java.util.UUID conflictingUser = null;
        try {
            jdbc.sql("DELETE FROM app_users WHERE id = :id").param("id", AdminAccountSeeder.ADMIN_ID).update();
            var barrier = new CyclicBarrier(2);
            Callable<AdminAccountSeeder.Result> seed = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return seeder.ensureAdministrator("Bootstrap Administrator", "09129999999", initialPassword);
            };
            try (var executor = Executors.newFixedThreadPool(2)) {
                var results = executor.invokeAll(List.of(seed, seed), 30, TimeUnit.SECONDS);
                assertThat(List.of(results.get(0).get(), results.get(1).get()))
                        .containsExactlyInAnyOrder(AdminAccountSeeder.Result.CREATED, AdminAccountSeeder.Result.SKIPPED);
            }
            jdbc.sql("DELETE FROM app_users WHERE id = :id").param("id", AdminAccountSeeder.ADMIN_ID).update();
            var other = auth.register("Unrelated User", "09129999999", "SyntheticPassword42!").user();
            conflictingUser = other.id();
            assertThatThrownBy(() -> seeder.ensureAdministrator("Bootstrap Administrator", "09129999999", initialPassword))
                    .isInstanceOf(IllegalStateException.class);
            var unchanged = auth.currentUser(other.id());
            assertThat(unchanged.type()).isEqualTo(UserType.USER);
            assertThat(unchanged.phoneNumber()).isEqualTo(other.phoneNumber());
            assertThat(unchanged.passwordHash().equals(other.passwordHash())).isTrue();
            assertThat(store.findUser(AdminAccountSeeder.ADMIN_ID)).isEmpty();
        } finally {
            if (conflictingUser != null) jdbc.sql("DELETE FROM app_users WHERE id = :id").param("id", conflictingUser).update();
            jdbc.sql("DELETE FROM app_users WHERE id = :id").param("id", AdminAccountSeeder.ADMIN_ID).update();
            store.insertBootstrapUserIfAbsent(original);
        }
    }
}
