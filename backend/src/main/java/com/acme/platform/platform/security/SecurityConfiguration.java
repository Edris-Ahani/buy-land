package com.acme.platform.platform.security;

import java.net.URI;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProblemHandler problems,
            @Value("${platform.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
        return http
                // This API accepts bearer headers only; browser cookies never authenticate a request.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/livez", "/readyz", "/actuator/health", "/actuator/health/**").permitAll()
                        // Prometheus is exposed exclusively on the unpublished management listener.
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/docs", "/docs/", "/docs/openapi.yaml", "/docs/scalar.js", "/docs/config.js")
                        .access((authentication, context) -> new AuthorizationDecision(apiDocsEnabled))
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/products", "/api/v1/products/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/products", "/api/v1/admin/products/{id}",
                                "/api/v1/admin/orders", "/api/v1/admin/orders/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/categories", "/api/v1/admin/products",
                                "/api/v1/admin/orders/{id}/confirm", "/api/v1/admin/orders/{id}/ship", "/api/v1/admin/orders/{id}/cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/categories/{id}", "/api/v1/admin/products/{id}").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/{id}").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/{id}/cancel").hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout-all").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/catalog").hasAuthority("SCOPE_catalog:write")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(userTypeAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems)
                        .withObjectPostProcessor(bearerFailureHandling(problems)))
                .build();
    }

    static JwtAuthenticationConverter userTypeAuthenticationConverter() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new ArrayList<>(scopes.convert(jwt));
            String type = jwt.getClaimAsString("type");
            if ("user".equals(type)) authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            else if ("admin".equals(type)) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            return authorities;
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${platform.cors.allowed-origins:}") String configuredOrigins) {
        var origins = Arrays.stream(configuredOrigins.split(",")).map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        for (String origin : origins) {
            URI uri = URI.create(origin);
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || !uri.getPath().isEmpty() || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("CORS origins must be exact HTTP or HTTPS origins without paths or wildcards");
            }
        }
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("X-Request-ID"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }

    private static ObjectPostProcessor<BearerTokenAuthenticationFilter> bearerFailureHandling(SecurityProblemHandler problems) {
        return new ObjectPostProcessor<>() {
            @Override
            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                var handler = new AuthenticationEntryPointFailureHandler(problems);
                handler.setRethrowAuthenticationServiceException(false);
                filter.setAuthenticationFailureHandler(handler);
                return filter;
            }
        };
    }
}
