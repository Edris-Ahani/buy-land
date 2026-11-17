package com.acme.platform.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.platform.auth.application.AuthService;
import com.acme.platform.auth.domain.AuthInput;
import com.acme.platform.auth.domain.UserAccount;
import com.acme.platform.platform.security.SecurityConfiguration;
import com.acme.platform.platform.security.SecurityProblemHandler;
import com.acme.platform.platform.web.ApiExceptionHandler;
import com.acme.platform.support.OpenApiContract;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class, ApiExceptionHandler.class, AuthExceptionHandler.class})
class AuthApiTest {
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String BODY = "{\"fullName\":\"Test User\",\"phoneNumber\":\"09120000001\",\"password\":\"ExamplePassword42!\"}";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UserAccount USER = new UserAccount(USER_ID, "Test User", "+989120000001", "private-password-hash", true, Instant.EPOCH);
    private static final AuthService.Tokens TOKENS = new AuthService.Tokens("test-access-token", "a".repeat(43), 900, 2_592_000, USER);
    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService service;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void anonymousRegistrationLoginAndRefreshReturnTheDocumentedTokenPair() throws Exception {
        given(service.register(anyString(), anyString(), anyString())).willReturn(TOKENS);
        assertResponse(post(REGISTER).content(BODY), REGISTER, "post", 201);
        given(service.login(anyString(), anyString())).willReturn(TOKENS);
        assertResponse(post("/api/v1/auth/login").content(BODY), "/api/v1/auth/login", "post", 200);
        given(service.refresh(anyString())).willReturn(TOKENS);
        assertResponse(post("/api/v1/auth/refresh").content("{\"refreshToken\":\"" + "a".repeat(43) + "\"}"), "/api/v1/auth/refresh", "post", 200);
    }

    @Test
    void validationAndMalformedJsonNeverEchoCredentials() throws Exception {
        assertResponse(post(REGISTER).content("{\"fullName\":\"\",\"phoneNumber\":\"\",\"password\":\"private-input\"}"), REGISTER, "post", 400);
        assertResponse(post(REGISTER).content("{\"password\":\"private-input\","), REGISTER, "post", 400);
        given(service.register(anyString(), anyString(), anyString())).willThrow(new AuthInput.InvalidField("phoneNumber"));
        assertResponse(post(REGISTER).content(BODY), REGISTER, "post", 400);
    }

    @Test
    void duplicateAndInvalidCredentialsUseCorrelatedProblemResponses() throws Exception {
        given(service.register(anyString(), anyString(), anyString())).willThrow(new AuthService.PhoneAlreadyRegistered());
        assertResponse(post(REGISTER).content(BODY), REGISTER, "post", 409);
        given(service.login(anyString(), anyString())).willThrow(new AuthService.InvalidCredentials());
        assertResponse(post("/api/v1/auth/login").content(BODY), "/api/v1/auth/login", "post", 401);
        given(service.refresh(anyString())).willThrow(new AuthService.InvalidCredentials());
        assertResponse(post("/api/v1/auth/refresh").content("{\"refreshToken\":\"private-input\"}"), "/api/v1/auth/refresh", "post", 401);
    }

    @Test
    void unexpectedErrorsAreSanitizedAndUnsupportedMediaIsRejected() throws Exception {
        given(service.register(anyString(), anyString(), anyString())).willThrow(new IllegalStateException("private-input"));
        assertResponse(post(REGISTER).content(BODY), REGISTER, "post", 500);
        var result = mvc.perform(post(REGISTER).contentType(MediaType.TEXT_PLAIN).content("private-input")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(415);
        new OpenApiContract().assertResponse("post", REGISTER, result.getResponse());
    }

    @Test
    void profileAndLogoutAllRequireAuthenticationAndNeverExposePasswordHashes() throws Exception {
        assertResponse(get("/api/v1/auth/me"), "/api/v1/auth/me", "get", 401);
        assertResponse(post("/api/v1/auth/logout-all"), "/api/v1/auth/logout-all", "post", 401);
        given(service.currentUser(USER_ID)).willReturn(USER);
        assertResponse(get("/api/v1/auth/me").with(jwt().jwt(token -> token.subject(USER_ID.toString()))), "/api/v1/auth/me", "get", 200);
        mvc.perform(post("/api/v1/auth/logout-all").with(jwt().jwt(token -> token.subject(USER_ID.toString())))).andExpect(status().isNoContent());
        var logout = mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + "a".repeat(43) + "\"}")).andReturn().getResponse();
        assertThat(logout.getStatus()).isEqualTo(204);
        assertThat(logout.getContentAsString()).isEmpty();
        assertThat(logout.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    private void assertResponse(MockHttpServletRequestBuilder request, String path, String method, int status) throws Exception {
        var response = mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        var body = new OpenApiContract().assertResponse(method, path, response);
        assertThat(response.getContentAsString()).doesNotContain("private-input", "private-password-hash");
        if (status >= 400) assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-ID"));
        else assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
