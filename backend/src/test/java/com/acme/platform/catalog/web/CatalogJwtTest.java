package com.acme.platform.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.platform.auth.api.SessionVerifier;
import com.acme.platform.catalog.application.CatalogService;
import com.acme.platform.catalog.domain.CatalogItem;
import com.acme.platform.auth.security.JwtConfiguration;
import com.acme.platform.platform.security.SecurityConfiguration;
import com.acme.platform.platform.security.SecurityProblemHandler;
import com.acme.platform.support.OpenApiContract;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CatalogController.class, properties = {
        "platform.auth.issuer=https://api.example.test", "platform.auth.audience=buy-land-api"
})
@Import({SecurityConfiguration.class, SecurityProblemHandler.class, JwtConfiguration.class})
class CatalogJwtTest {
    private static final byte[] KEY = new SecureRandom().generateSeed(48);
    private static final String BODY = "{\"slug\":\"signed-item\",\"name\":\"Signed item\",\"description\":\"\"}";
    @Autowired private MockMvc mvc;
    @MockitoBean private CatalogService service;
    @MockitoBean private SessionVerifier sessions;

    @DynamicPropertySource
    static void signingKey(DynamicPropertyRegistry properties) {
        properties.add("platform.auth.jwt-secret", () -> Base64.getEncoder().encodeToString(KEY));
    }

    @BeforeEach
    void activeSession() { given(sessions.isActive(any(), any())).willReturn(true); }

    @Test
    void acceptsTheLocalSignatureIssuerAudienceAndAuthorizedScope() throws Exception {
        given(service.create(anyString(), anyString(), anyString())).willReturn(
                new CatalogItem(UUID.randomUUID(), "signed-item", "Signed item", "", Instant.EPOCH));
        request(token("https://api.example.test", "buy-land-api", "catalog:write", Instant.now().plusSeconds(300), KEY, "access"), 201);
    }

    @Test
    void rejectsInvalidIssuerAudienceSignatureExpirationAndTokenUse() throws Exception {
        request(token("https://other.example.test", "buy-land-api", "catalog:write", Instant.now().plusSeconds(300), KEY, "access"), 401);
        request(token("https://api.example.test", "other-api", "catalog:write", Instant.now().plusSeconds(300), KEY, "access"), 401);
        request(token("https://api.example.test", "buy-land-api", "catalog:write", Instant.now().minusSeconds(1), KEY, "access"), 401);
        request(token("https://api.example.test", "buy-land-api", "catalog:write", Instant.now().plusSeconds(300), new byte[48], "access"), 401);
        request(token("https://api.example.test", "buy-land-api", "catalog:write", Instant.now().plusSeconds(300), KEY, "refresh"), 401);
    }

    @Test
    void revocationIsCheckedForEveryAuthenticatedRequest() throws Exception {
        given(sessions.isActive(any(), any())).willReturn(false);
        request(token("https://api.example.test", "buy-land-api", "catalog:write", Instant.now().plusSeconds(300), KEY, "access"), 401);
    }

    @Test
    void ordinaryUsersDoNotReceiveCatalogWriteAccess() throws Exception {
        request(token("https://api.example.test", "buy-land-api", "user", Instant.now().plusSeconds(300), KEY, "access"), 403);
    }

    @Test
    void sessionStoreFailuresDenyAccessWithoutLeakingInfrastructureDetails() throws Exception {
        given(sessions.isActive(any(), any())).willThrow(new IllegalStateException("private-database-details"));
        String token = token("https://api.example.test", "buy-land-api", "catalog:write", Instant.now().plusSeconds(300), KEY, "access");
        var response = mvc.perform(post("/api/v1/catalog").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).doesNotContain("private-database-details");
        new OpenApiContract().assertResponse("post", "/api/v1/catalog", response);
    }

    private void request(String token, int expected) throws Exception {
        mvc.perform(post("/api/v1/catalog").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().is(expected));
    }

    private static String token(String issuer, String audience, String scope, Instant expiration, byte[] key, String use) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now().minusSeconds(60))).expirationTime(Date.from(expiration))
                .claim("scope", scope).claim("sid", UUID.randomUUID().toString()).claim("token_use", use).build();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(key));
        return token.serialize();
    }
}
