package com.spotfinder.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

class DeploymentSecretsTest {

    @ParameterizedTest
    @ValueSource(strings = {"JWT_SECRET", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD",
            "MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "MINIO_ACCESS_KEY", "MINIO_SECRET_KEY"})
    void missingSecret_shouldRejectDeploymentWithoutPrintingValues(String name) throws Exception {
        Map<String, String> settings = validSettings();
        settings.remove(name);
        Result result = validate(settings, false);
        assertThat(result.code()).isNotZero();
        assertThat(result.output()).contains(name).doesNotContain("fixture-database-password");
    }

    @Test
    void validSettings_shouldPassWithoutExposingSecretsEvenIfTracingWasRequested() throws Exception {
        Map<String, String> settings = validSettings();
        Result result = validate(settings, true);
        assertThat(result.code()).isZero();
        assertThat(result.output()).contains("passed validation");
        settings.values().forEach(value -> assertThat(result.output()).doesNotContain(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "short", "change-me-change-me-change-me-change-me-change-me",
            "local-only-jwt-key-never-use-outside-local-development",
            "test-only-jwt-key-for-automated-tests-never-deploy"})
    void invalidJwt_shouldRejectDeployment(String value) throws Exception {
        Map<String, String> settings = validSettings();
        settings.put("JWT_SECRET", value);
        Result result = validate(settings, false);
        assertThat(result.code()).isNotZero();
        assertThat(result.output()).contains("JWT_SECRET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"SPRING_DATASOURCE_PASSWORD", "MINIO_ROOT_PASSWORD", "MINIO_SECRET_KEY"})
    void standardPasswords_shouldRejectDeployment(String name) throws Exception {
        Map<String, String> settings = validSettings();
        settings.put(name, "adminadmin");
        Result result = validate(settings, false);
        assertThat(result.code()).isNotZero();
        assertThat(result.output()).contains(name);
    }

    @Test
    void applicationCannotUseTheRootMinioAccount() throws Exception {
        Map<String, String> settings = validSettings();
        settings.put("MINIO_ACCESS_KEY", settings.get("MINIO_ROOT_USER"));
        assertThat(validate(settings, false).code()).isNotZero();
    }

    @Test
    void metacharactersInSecrets_shouldRemainData() throws Exception {
        Map<String, String> settings = validSettings();
        settings.put("SPRING_DATASOURCE_PASSWORD", "fixture-$(exit 99);\"password'&%");
        assertThat(validate(settings, false).code()).isZero();
    }

    @Test
    void workflow_shouldCheckSecretsBeforeAnyContainerMutationAndKeepThemOutOfShellSource() throws Exception {
        try (InputStream stream = Files.newInputStream(Path.of(".gitea/workflows/stage.yml"))) {
            Map<?, ?> workflow = new Yaml().load(stream);
            Map<?, ?> jobs = (Map<?, ?>) workflow.get("jobs");
            Map<?, ?> deployment = (Map<?, ?>) jobs.get("build-and-deploy-stage");
            Map<?, ?> environment = (Map<?, ?>) deployment.get("env");
            for (String name : validSettings().keySet()) {
                assertThat(environment.get(name)).isEqualTo("${{ secrets." + name + " }}");
            }
            List<?> steps = (List<?>) deployment.get("steps");
            boolean validated = false;
            for (Object item : steps) {
                String command = String.valueOf(((Map<?, ?>) item).get("run"));
                assertThat(command).doesNotContain("${{ secrets.");
                if (command.contains("bash .gitea/scripts/validate-secrets.sh")) {
                    validated = true;
                }
                if (command.contains("docker rm") || command.contains("docker run -d")) {
                    assertThat(validated).as("deployment secrets are validated before container mutation").isTrue();
                }
            }
            assertThat(validated).isTrue();
        }
    }

    private Result validate(Map<String, String> settings, boolean trace) throws Exception {
        String bash = System.getProperty("os.name").startsWith("Windows")
                ? "C:/Program Files/Git/bin/bash.exe" : "bash";
        ProcessBuilder builder = trace
                ? new ProcessBuilder(bash, "-x", ".gitea/scripts/validate-secrets.sh")
                : new ProcessBuilder(bash, ".gitea/scripts/validate-secrets.sh");
        for (String name : validSettings().keySet()) {
            builder.environment().remove(name);
        }
        builder.environment().putAll(settings);
        Process process = builder.redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS))
                    .as("preflight finishes without contacting services").isTrue();
            return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8));
        } finally {
            process.destroyForcibly();
        }
    }

    private Map<String, String> validSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("JWT_SECRET", "fixture-jwt-key-not-a-real-secret-0123456789");
        settings.put("SPRING_DATASOURCE_USERNAME", "fixture-user");
        settings.put("SPRING_DATASOURCE_PASSWORD", "fixture-database-password");
        settings.put("MINIO_ROOT_USER", "fixture-root");
        settings.put("MINIO_ROOT_PASSWORD", "fixture-root-password");
        settings.put("MINIO_ACCESS_KEY", "fixture-application");
        settings.put("MINIO_SECRET_KEY", "fixture-storage-password");
        return settings;
    }

    private record Result(int code, String output) {
    }
}
