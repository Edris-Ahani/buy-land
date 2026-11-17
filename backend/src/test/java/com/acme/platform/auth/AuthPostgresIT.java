package com.acme.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.acme.platform.support.OpenApiContract;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthPostgresIT.Containers.class)
class AuthPostgresIT {
    private static final String PASSWORD = "ExamplePassword42!";
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient jdbc;
    @Autowired private JwtDecoder decoder;

    @Test
    void registrationPersistsOnlyHashesAndReturnsAnImmediatelyUsableJwt() throws Exception {
        String phone = phone();
        JsonNode tokens = register(phone);
        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.path("expiresIn").asInt()).isBetween(899, 900);
        assertThat(tokens.path("refreshExpiresIn").asInt()).isBetween(2_591_999, 2_592_000);
        assertThat(tokens.path("user").path("phoneNumber").asText()).isEqualTo("+98" + phone.substring(1));
        var jwt = decoder.decode(tokens.path("accessToken").asText());
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("user");
        assertThat(jwt.getClaims()).doesNotContainKeys("phoneNumber", "password", "passwordHash", "fullName");
        assertMe(tokens, 200);
        String passwordHash = jdbc.sql("SELECT password_hash FROM app_users WHERE id = :id")
                .param("id", UUID.fromString(tokens.path("user").path("id").asText())).query(String.class).single();
        assertThat(passwordHash).startsWith("{pbkdf2-sha256-600000}").doesNotContain(PASSWORD);
        String refreshHash = jdbc.sql("SELECT token_hash FROM auth_refresh_tokens WHERE session_id = :id")
                .param("id", UUID.fromString(jwt.getClaimAsString("sid"))).query(String.class).single();
        assertThat(refreshHash).matches("[a-f0-9]{64}").isNotEqualTo(tokens.path("refreshToken").asText());
        postJson("/api/v1/auth/register", registrationBody("+98" + phone.substring(1)), 409);
        var write = mvc.perform(post("/api/v1/catalog").header("Authorization", "Bearer " + tokens.path("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"slug\":\"unauthorized-write\",\"name\":\"Test\",\"description\":\"\"}")).andReturn();
        assertThat(write.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void loginCreatesIndependentSessionsAndRejectsIncorrectCredentials() throws Exception {
        String phone = phone();
        JsonNode registration = register(phone);
        JsonNode login = login(phone);
        assertThat(login.path("refreshToken").asText()).isNotEqualTo(registration.path("refreshToken").asText());
        assertThat(login.path("user").path("id").asText()).isEqualTo(registration.path("user").path("id").asText());
        postJson("/api/v1/auth/login", "{\"phoneNumber\":\"" + phone + "\",\"password\":\"IncorrectPassword42!\"}", 401);
        postJson("/api/v1/auth/login", "{\"phoneNumber\":\"" + phone() + "\",\"password\":\"IncorrectPassword42!\"}", 401);
        assertMe(login, 200);
    }

    @Test
    void refreshRotationDetectsReplayAndRevokesOnlyTheCompromisedSession() throws Exception {
        String phone = phone();
        JsonNode original = register(phone);
        JsonNode independent = login(phone);
        JsonNode rotated = postJson("/api/v1/auth/refresh", refreshBody(original), 200);
        assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(original.path("refreshToken").asText());
        assertMe(rotated, 200);
        postJson("/api/v1/auth/refresh", refreshBody(original), 401);
        postJson("/api/v1/auth/refresh", refreshBody(rotated), 401);
        assertMe(original, 401);
        assertMe(rotated, 401);
        assertMe(independent, 200);
    }

    @Test
    void simultaneousRefreshRequestsCannotBothSucceedAndReplayRevocationCommits() throws Exception {
        JsonNode tokens = register(phone());
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<MvcResult> refresh = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return rawPost("/api/v1/auth/refresh", refreshBody(tokens));
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(refresh, refresh), 20, TimeUnit.SECONDS);
            MvcResult first = results.get(0).get();
            MvcResult second = results.get(1).get();
            assertThat(List.of(first.getResponse().getStatus(), second.getResponse().getStatus())).containsExactlyInAnyOrder(200, 401);
            JsonNode winner = body(first.getResponse().getStatus() == 200 ? first : second);
            postJson("/api/v1/auth/refresh", refreshBody(winner), 401);
            assertMe(winner, 401);
        }
    }

    @Test
    void simultaneousRegistrationUsesTheDatabaseUniqueConstraint() throws Exception {
        String phone = phone();
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<Integer> register = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return rawPost("/api/v1/auth/register", registrationBody(phone)).getResponse().getStatus();
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(register, register), 20, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM app_users WHERE phone_number = :phone")
                .param("phone", "+98" + phone.substring(1)).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void logoutAcceptsConsumedTokensAndRevokesAccessAndRefreshImmediately() throws Exception {
        String phone = phone();
        JsonNode original = register(phone);
        JsonNode independent = login(phone);
        JsonNode rotated = postJson("/api/v1/auth/refresh", refreshBody(original), 200);
        postJson("/api/v1/auth/logout", refreshBody(original), 204);
        postJson("/api/v1/auth/logout", refreshBody(original), 204);
        assertMe(rotated, 401);
        postJson("/api/v1/auth/refresh", refreshBody(rotated), 401);
        assertMe(independent, 200);
        postJson("/api/v1/auth/logout", "{\"refreshToken\":\"" + "x".repeat(43) + "\"}", 204);
    }

    @Test
    void logoutAllRevokesEveryExistingSession() throws Exception {
        String phone = phone();
        JsonNode first = register(phone);
        JsonNode second = login(phone);
        var response = mvc.perform(post("/api/v1/auth/logout-all")
                .header("Authorization", "Bearer " + first.path("accessToken").asText())).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(204);
        assertMe(first, 401);
        assertMe(second, 401);
        postJson("/api/v1/auth/refresh", refreshBody(first), 401);
        postJson("/api/v1/auth/refresh", refreshBody(second), 401);
    }

    @Test
    void expiredSessionsAndDisabledUsersCannotAuthenticateOrRefresh() throws Exception {
        JsonNode expired = register(phone());
        UUID session = UUID.fromString(decoder.decode(expired.path("accessToken").asText()).getClaimAsString("sid"));
        jdbc.sql("UPDATE auth_sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = :id")
                .param("id", session).update();
        assertMe(expired, 401);
        postJson("/api/v1/auth/refresh", refreshBody(expired), 401);
        String phone = phone();
        JsonNode disabled = register(phone);
        jdbc.sql("UPDATE app_users SET enabled = false WHERE id = :id")
                .param("id", UUID.fromString(disabled.path("user").path("id").asText())).update();
        assertMe(disabled, 401);
        postJson("/api/v1/auth/refresh", refreshBody(disabled), 401);
        postJson("/api/v1/auth/login", loginBody(phone), 401);
    }

    private JsonNode register(String phone) throws Exception { return postJson("/api/v1/auth/register", registrationBody(phone), 201); }
    private JsonNode login(String phone) throws Exception { return postJson("/api/v1/auth/login", loginBody(phone), 200); }
    private static String registrationBody(String phone) {
        return "{\"fullName\":\"\\u0627\\u062f\\u0631\\u06cc\\u0633 \\u0622\\u0647\\u0646\\u06cc\",\"phoneNumber\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\"}";
    }
    private static String loginBody(String phone) { return "{\"phoneNumber\":\"" + phone + "\",\"password\":\"" + PASSWORD + "\"}"; }
    private static String refreshBody(JsonNode tokens) { return "{\"refreshToken\":\"" + tokens.path("refreshToken").asText() + "\"}"; }
    private static String phone() { return "09" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L)); }

    private MvcResult rawPost(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private JsonNode postJson(String path, String json, int expected) throws Exception {
        var result = rawPost(path, json);
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        if (expected == 204) {
            assertThat(result.getResponse().getContentAsString()).isEmpty();
            assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
            return mapper.nullNode();
        }
        return new OpenApiContract().assertResponse("post", path, result.getResponse());
    }

    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }

    private void assertMe(JsonNode tokens, int expected) throws Exception {
        var result = mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.path("accessToken").asText())).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        new OpenApiContract().assertResponse("get", "/api/v1/auth/me", result.getResponse());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("docker.io/library/postgres:18.6-alpine").asCompatibleSubstituteFor("postgres"));
        }
    }
}
