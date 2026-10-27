package com.spotfinder.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotfinder.auth.security.JwtService;
import com.spotfinder.storage.config.MinioConfig;
import io.minio.MinioClient;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

class SecretsConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
            "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "JWT_SECRET"})
    void missingSetting_shouldStopStartupBeforeClientsAreCreated(String name) {
        Map<String, String> settings = validSettings();
        settings.remove(name);
        AtomicBoolean connected = new AtomicBoolean();

        runner(settings, "prod").withBean("clientProbe", Object.class, () -> {
            connected.set(true);
            return new Object();
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(name);
            assertThat(connected).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
            "MINIO_ENDPOINT", "MINIO_BUCKET", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY", "JWT_SECRET"})
    void blankSetting_shouldStopStartup(String name) {
        Map<String, String> settings = validSettings();
        settings.put(name, "   ");

        runner(settings, "prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(name);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "SPRING_DATASOURCE_PASSWORD,admin", "SPRING_DATASOURCE_PASSWORD,ADMIN",
            "MINIO_SECRET_KEY,adminadmin", "MINIO_SECRET_KEY,minioadmin",
            "JWT_SECRET,change-me-change-me-change-me-change-me-change-me",
            "JWT_SECRET,local-only-jwt-key-never-use-outside-local-development",
            "JWT_SECRET,test-only-jwt-key-for-automated-tests-never-deploy",
            "SPRING_DATASOURCE_PASSWORD,local-only-database-password",
            "MINIO_SECRET_KEY,test-only-storage-password"
    })
    void publicEnvironment_shouldRejectKnownDefaultsAndDevelopmentValues(String name, String value) {
        Map<String, String> settings = validSettings();
        settings.put(name, value);

        runner(settings, "prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining(name);
        });
    }

    @Test
    void shortJwt_shouldFailWithoutIncludingItsValueInTheException() {
        String sensitive = "sensitive-value-too-short";
        Map<String, String> settings = validSettings();
        settings.put("JWT_SECRET", sensitive);

        runner(settings, "prod").run(context -> {
            assertThat(context).hasFailed();
            StringWriter trace = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(trace));
            assertThat(trace.toString()).contains("JWT_SECRET", "32 UTF-8 bytes").doesNotContain(sensitive);
        });
    }

    @Test
    void shortMinioSecret_shouldFailBeforeTheSdkCanInitialize() {
        Map<String, String> settings = validSettings();
        settings.put("MINIO_SECRET_KEY", "short");
        runner(settings, "prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("MINIO_SECRET_KEY");
        });
    }

    @Test
    void missingSecrets_shouldFailEvenWhenBucketInitializationIsDisabled() {
        Map<String, String> settings = validSettings();
        settings.remove("MINIO_SECRET_KEY");
        settings.put("storage.minio.initialize-bucket", "false");
        runner(settings, "prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("MINIO_SECRET_KEY");
        });
    }

    @Test
    void validSecrets_shouldInitializeJwtAndStorageWithNoActiveProfile() {
        runner(validSettings()).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class).hasSingleBean(MinioClient.class);
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5433/fixture");
            assertThat(context.getEnvironment().getProperty("storage.minio.endpoint"))
                    .isEqualTo("http://fixture-minio:9000");
            assertThat(context.getEnvironment().getProperty("storage.minio.bucket")).isEqualTo("fixture-bucket");
        });
    }

    @Test
    void missingNonSecretOverrides_shouldUseExistingConnectionDefaults() {
        Map<String, String> settings = validSettings();
        settings.remove("SPRING_DATASOURCE_URL");
        settings.remove("MINIO_ENDPOINT");
        settings.remove("MINIO_BUCKET");

        runner(settings, "prod").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class).hasSingleBean(MinioClient.class);
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost:5433/spotfinder");
            assertThat(context.getEnvironment().getProperty("storage.minio.endpoint"))
                    .isEqualTo("http://localhost:9000");
            assertThat(context.getEnvironment().getProperty("storage.minio.bucket")).isEqualTo("spotfinder-local");
        });
    }

    @Test
    void explicitLocalProfile_shouldProvideLocalComposeDefaults() {
        runner(Map.of(), "local").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class).hasSingleBean(MinioClient.class);
            assertThat(context.getEnvironment().getProperty("app.auth.cookie-secure")).isEqualTo("false");
        });
    }

    @Test
    void mixingLocalAndProd_shouldNotAllowDevelopmentCredentials() {
        runner(Map.of(), "prod", "local").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void explicitTestProfile_shouldUseOnlyTestResources() {
        runner(Map.of(), "test").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtService.class).hasSingleBean(MinioClient.class);
            assertThat(context.getEnvironment().getProperty("storage.minio.initialize-bucket")).isEqualTo("false");
        });
    }

    private ApplicationContextRunner runner(Map<String, String> settings, String... profiles) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(SecretsConfiguration.class, JwtService.class, MinioConfig.class)
                .withPropertyValues(settings.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
        if (profiles.length > 0) {
            runner = runner.withPropertyValues("spring.profiles.active=" + String.join(",", profiles));
        }
        return runner;
    }

    private Map<String, String> validSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5433/fixture");
        settings.put("SPRING_DATASOURCE_USERNAME", "fixture-user");
        settings.put("SPRING_DATASOURCE_PASSWORD", "fixture-database-password");
        settings.put("MINIO_ENDPOINT", "http://fixture-minio:9000");
        settings.put("MINIO_BUCKET", "fixture-bucket");
        settings.put("MINIO_ACCESS_KEY", "fixture-application");
        settings.put("MINIO_SECRET_KEY", "fixture-storage-password");
        settings.put("JWT_SECRET", "fixture-jwt-key-not-a-real-secret-0123456789");
        return settings;
    }
}
