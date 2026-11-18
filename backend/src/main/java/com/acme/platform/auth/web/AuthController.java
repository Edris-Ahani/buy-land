package com.acme.platform.auth.web;

import com.fasterxml.jackson.annotation.JsonValue;
import com.acme.platform.auth.application.AuthService;
import com.acme.platform.auth.domain.AuthInput;
import com.acme.platform.auth.domain.UserAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) { this.service = service; }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return tokens(service.register(request.fullName(), request.phoneNumber(), request.password()), HttpStatus.CREATED);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokens(service.login(request.phoneNumber(), request.password()), HttpStatus.OK);
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return tokens(service.refresh(request.refreshToken()), HttpStatus.OK);
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        service.logoutAll(UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(UserResponse.from(service.currentUser(UUID.fromString(jwt.getSubject()))));
    }

    private static ResponseEntity<TokenResponse> tokens(AuthService.Tokens result, HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
                .body(new TokenResponse(result.accessToken(), result.refreshToken(), "Bearer", result.expiresIn(),
                        result.refreshExpiresIn(), UserResponse.from(result.user())));
    }

    public record RegisterRequest(@NotBlank @Size(max = AuthInput.MAX_NAME_LENGTH) String fullName,
            @NotBlank @Size(max = 32) String phoneNumber,
            @NotBlank @Size(min = AuthInput.MIN_PASSWORD_LENGTH, max = AuthInput.MAX_PASSWORD_LENGTH) String password,
            RegistrationType type) {
        public RegisterRequest {
            if (type == null) type = RegistrationType.USER;
        }
        @Override public String toString() { return "RegisterRequest[REDACTED]"; }
    }

    @JsonDeserialize(using = RegistrationTypeDeserializer.class)
    public enum RegistrationType {
        USER;

        @JsonValue
        public String value() { return "user"; }

    }

    public static final class RegistrationTypeDeserializer extends ValueDeserializer<RegistrationType> {
        @Override
        public RegistrationType deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() == JsonToken.VALUE_STRING && "user".equals(parser.getString())) {
                return RegistrationType.USER;
            }
            return context.reportInputMismatch(RegistrationType.class, "Public registration only accepts the user type");
        }

        @Override
        public RegistrationType getAbsentValue(DeserializationContext context) { return RegistrationType.USER; }

        @Override
        public RegistrationType getNullValue(DeserializationContext context) {
            return context.reportInputMismatch(RegistrationType.class, "Registration type must not be null");
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 32) String phoneNumber,
            @NotBlank @Size(min = AuthInput.MIN_PASSWORD_LENGTH, max = AuthInput.MAX_PASSWORD_LENGTH) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }

    public record RefreshRequest(@NotBlank @Size(max = 256) String refreshToken) {
        @Override public String toString() { return "RefreshRequest[REDACTED]"; }
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
            long refreshExpiresIn, UserResponse user) {
        @Override public String toString() { return "TokenResponse[REDACTED]"; }
    }

    public record UserResponse(UUID id, String fullName, String phoneNumber, String type, Instant createdAt) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.id(), user.fullName(), user.phoneNumber(), user.type().value(), user.createdAt());
        }
    }
}
