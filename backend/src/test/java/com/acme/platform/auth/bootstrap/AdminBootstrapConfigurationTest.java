package com.acme.platform.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.platform.auth.application.AdminAccountSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AdminBootstrapConfigurationTest {
    @Test
    void seedCompletesBeforeApplicationLifecycleServicesStart() {
        var seeder = mock(AdminAccountSeeder.class);
        given(seeder.ensureAdministrator("", "", "")).willReturn(AdminAccountSeeder.Result.SKIPPED);
        var lifecycle = new SeedDependentLifecycle(seeder);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AdminAccountSeeder.class, () -> seeder);
            context.registerBean(SmartLifecycle.class, () -> lifecycle);
            context.register(AdminBootstrapConfiguration.class);
            context.refresh();
            assertThat(lifecycle.isRunning()).isTrue();
        }
    }

    @Test
    void startupFailureNeverExposesCredentialsOrThePersistenceExceptionChain() {
        var seeder = mock(AdminAccountSeeder.class);
        given(seeder.ensureAdministrator("", "", "")).willThrow(new IllegalStateException("private-input"));
        var lifecycle = new SeedDependentLifecycle(seeder);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AdminAccountSeeder.class, () -> seeder);
            context.registerBean(SmartLifecycle.class, () -> lifecycle);
            context.register(AdminBootstrapConfiguration.class);
            assertThatThrownBy(context::refresh).isInstanceOf(IllegalStateException.class)
                    .hasMessageNotContaining("private-input").hasNoCause();
            assertThat(lifecycle.isRunning()).isFalse();
        }
    }

    private static final class SeedDependentLifecycle implements SmartLifecycle {
        private final AdminAccountSeeder seeder;
        private boolean running;

        private SeedDependentLifecycle(AdminAccountSeeder seeder) { this.seeder = seeder; }

        @Override public void start() { verify(seeder).ensureAdministrator("", "", ""); running = true; }
        @Override public void stop() { running = false; }
        @Override public boolean isRunning() { return running; }
    }
}
