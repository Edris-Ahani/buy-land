package com.acme.platform.catalog.web;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.platform.catalog.application.CatalogRepository;
import com.acme.platform.catalog.application.CatalogService;
import com.acme.platform.platform.security.SecurityConfiguration;
import com.acme.platform.platform.security.SecurityProblemHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CatalogController.class,
        properties = "platform.cors.allowed-origins=http://localhost:3000,http://localhost:5173")
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class CatalogCorsTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CatalogService service;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void permitsJsonAndBearerPreflightsWithoutCredentials() throws Exception {
        for (String path : List.of("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/catalog")) {
            mvc.perform(options(path).header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "authorization,content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
    }

    @Test
    void exposesTheRequestIdentifierOnPublicResponses() throws Exception {
        given(service.list(anyInt(), anyInt())).willReturn(new CatalogRepository.CatalogPage(List.of(), 0, 12, 0, 0));
        mvc.perform(get("/api/v1/catalog").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-Request-ID"));
    }

    @Test
    void allowedOriginsStillRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void rejectsOtherOriginsPortsMethodsAndHeaders() throws Exception {
        for (String origin : List.of("https://other.example.test", "http://localhost:3001", "null")) {
            mvc.perform(get("/api/v1/catalog").header("Origin", origin))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
        mvc.perform(options("/api/v1/catalog").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "DELETE"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/catalog").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "x-private-header"))
                .andExpect(status().isForbidden());
    }
}
