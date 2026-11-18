package com.acme.platform.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class UserTypeAuthoritiesTest {
    @Test
    void signedTypesBecomeDistinctRolesWithoutGrantingAdditionalScopes() {
        assertThat(authorities("user")).containsExactlyInAnyOrder("FACTOR_BEARER", "SCOPE_user", "ROLE_USER");
        assertThat(authorities("admin")).containsExactlyInAnyOrder("FACTOR_BEARER", "SCOPE_user", "ROLE_ADMIN");
    }

    @Test
    void missingOrUnknownTypesNeverGrantARole() {
        assertThat(authorities(null)).containsExactlyInAnyOrder("FACTOR_BEARER", "SCOPE_user");
        assertThat(authorities("ADMIN")).containsExactlyInAnyOrder("FACTOR_BEARER", "SCOPE_user");
        assertThat(authorities("superuser")).containsExactlyInAnyOrder("FACTOR_BEARER", "SCOPE_user");
    }

    private List<String> authorities(String type) {
        var jwt = Jwt.withTokenValue("test-token").header("alg", "HS256").subject("test-user").claim("scope", "user");
        if (type != null) jwt.claim("type", type);
        return SecurityConfiguration.userTypeAuthenticationConverter().convert(jwt.build()).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
    }
}
