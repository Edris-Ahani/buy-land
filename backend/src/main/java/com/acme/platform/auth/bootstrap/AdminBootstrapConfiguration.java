package com.acme.platform.auth.bootstrap;

import com.acme.platform.auth.application.AdminAccountSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminBootstrapConfiguration.class);

    @Bean
    @DependsOnDatabaseInitialization
    SmartInitializingSingleton bootstrapAdministrator(AdminAccountSeeder seeder,
            @Value("${platform.auth.bootstrap-admin.full-name:}") String fullName,
            @Value("${platform.auth.bootstrap-admin.phone-number:}") String phoneNumber,
            @Value("${platform.auth.bootstrap-admin.password:}") String password) {
        return () -> {
            try {
                var result = seeder.ensureAdministrator(fullName, phoneNumber, password);
                LOGGER.info("Bootstrap administrator initialization: {}", result);
            } catch (RuntimeException exception) {
                // Startup diagnostics must not expose credentials, SQL details, or exception chains.
                throw new IllegalStateException("Administrator initialization failed; check private bootstrap settings and account conflicts");
            }
        };
    }
}
