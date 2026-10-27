package com.spotfinder.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class SecretsConfiguration {

    private static final Set<String> DEFAULT_PASSWORDS = Set.of(
            "admin", "adminadmin", "minioadmin", "password", "changeme", "change-me");

    @Bean
    public static BeanFactoryPostProcessor validateRequiredSecrets(Environment environment) {
        // Run before datasource clients, Flyway and storage initialization can connect anywhere.
        return beanFactory -> validate(environment);
    }

    private static void validate(Environment environment) {
        boolean local = onlyProfile(environment, "local");
        boolean test = onlyProfile(environment, "test");
        required(environment, "spring.datasource.url", "SPRING_DATASOURCE_URL");
        required(environment, "spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
        String password = required(environment, "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");
        validatePassword(password, "SPRING_DATASOURCE_PASSWORD", local, test);

        required(environment, "storage.minio.endpoint", "MINIO_ENDPOINT");
        required(environment, "storage.minio.bucket", "MINIO_BUCKET");
        required(environment, "storage.minio.access-key", "MINIO_ACCESS_KEY");
        String minioSecret = required(environment, "storage.minio.secret-key", "MINIO_SECRET_KEY");
        validatePassword(minioSecret, "MINIO_SECRET_KEY", local, test);
        if (minioSecret.getBytes(StandardCharsets.UTF_8).length < 8) {
            throw invalid("MINIO_SECRET_KEY", "must contain at least 8 UTF-8 bytes");
        }

        String jwt = required(environment, "app.jwt.secret", "JWT_SECRET");
        if (jwt.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw invalid("JWT_SECRET", "must contain at least 32 UTF-8 bytes");
        }
        if (jwt.strip().toLowerCase(Locale.ROOT).startsWith("change-me")) {
            throw invalid("JWT_SECRET", "must not use the former built-in signing key");
        }
        rejectDevelopmentValue(jwt, "JWT_SECRET", local, test);
    }

    private static boolean onlyProfile(Environment environment, String profile) {
        return Arrays.equals(environment.getActiveProfiles(), new String[]{profile});
    }

    private static void validatePassword(String value, String name, boolean local, boolean test) {
        if (!local && DEFAULT_PASSWORDS.contains(value.strip().toLowerCase(Locale.ROOT))) {
            throw invalid(name, "must not use a standard password");
        }
        rejectDevelopmentValue(value, name, local, test);
    }

    private static void rejectDevelopmentValue(String value, String name, boolean local, boolean test) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if ((!local && normalized.startsWith("local-only-"))
                || (!test && normalized.startsWith("test-only-"))) {
            throw invalid(name, "must not use a local or test credential in this environment");
        }
    }

    private static String required(Environment environment, String property, String name) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (IllegalArgumentException exception) {
            // Do not attach the resolver's exception: it can contain configured values.
            throw invalid(name, "must be configured");
        }
        if (value == null || value.isBlank()) {
            throw invalid(name, "must be configured and non-blank");
        }
        return value;
    }

    private static IllegalStateException invalid(String name, String reason) {
        return new IllegalStateException(name + " " + reason);
    }
}
