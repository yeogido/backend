package com.yeogido.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ActuatorHealthConfigTest {

    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    @Test
    void productionHealthCheckIncludesDatabaseAndRedisWithoutDetails() throws Exception {
        Binder binder = binderFor("application.yml", "application-prod.yml");

        assertThat(binder.bind("management.endpoints.enabled-by-default", Boolean.class).get())
                .isFalse();
        assertThat(binder.bind("management.endpoints.web.exposure.include", String.class).get())
                .isEqualTo("health");
        assertThat(binder.bind("management.endpoint.health.enabled", Boolean.class).get())
                .isTrue();
        assertThat(binder.bind("management.endpoint.health.show-details", String.class).get())
                .isEqualTo("never");
        assertThat(binder.bind("management.health.db.enabled", Boolean.class).get())
                .isTrue();
        assertThat(binder.bind("management.health.redis.enabled", Boolean.class).get())
                .isTrue();
    }

    @Test
    void testProfileKeepsRedisHealthIndicatorDisabled() throws Exception {
        Binder binder = binderFor("application.yml", "application-test.yml");

        assertThat(binder.bind("management.health.redis.enabled", Boolean.class).get())
                .isFalse();
    }

    @Test
    void healthEndpointIsDownWhenDatabaseHealthIsDown() {
        assertAggregatedHealthStatus(
                () -> Health.down().build(),
                () -> Health.up().build(),
                Status.DOWN
        );
    }

    @Test
    void healthEndpointIsDownWhenRedisHealthIsDown() {
        assertAggregatedHealthStatus(
                () -> Health.up().build(),
                () -> Health.down().build(),
                Status.DOWN
        );
    }

    private void assertAggregatedHealthStatus(
            HealthIndicator dbHealthIndicator,
            HealthIndicator redisHealthIndicator,
            Status expectedStatus
    ) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        HealthContributorAutoConfiguration.class,
                        HealthEndpointAutoConfiguration.class
                ))
                .withPropertyValues(
                        "management.endpoint.health.enabled=true",
                        "management.endpoint.health.show-details=never"
                )
                .withBean("dbHealthIndicator", HealthIndicator.class, () -> dbHealthIndicator)
                .withBean("redisHealthIndicator", HealthIndicator.class, () -> redisHealthIndicator)
                .run(context -> assertThat(context.getBean(HealthEndpoint.class).health().getStatus())
                        .isEqualTo(expectedStatus));
    }

    private Binder binderFor(String baseConfig, String profileConfig) throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();

        for (PropertySource<?> propertySource : loadYaml(profileConfig)) {
            propertySources.addLast(propertySource);
        }
        for (PropertySource<?> propertySource : loadYaml(baseConfig)) {
            propertySources.addLast(propertySource);
        }

        return new Binder(ConfigurationPropertySources.from(propertySources));
    }

    private Iterable<PropertySource<?>> loadYaml(String path) throws IOException {
        return yamlLoader.load(path, new ClassPathResource(path));
    }
}
