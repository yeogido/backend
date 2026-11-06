package com.yeogido.backend.global.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.global.exception.GlobalExceptionHandler;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingProfileTest {

    private static final String REQUEST_ID = "b0db4288-286f-46ce-89fa-268db2ec6742";
    private static final String PROBE_MESSAGE = "logging-profile-probe";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void prodOutputsNativeJsonWithMdcAndSafeErrorFields() throws Exception {
        String output = runProbe("prod");
        assertThat(output.lines().filter(line -> !line.isBlank()).toList())
                .allSatisfy(line -> assertThat(line).startsWith("{"));
        List<JsonNode> logs = output.lines()
                .filter(line -> line.startsWith("{"))
                .map(this::readJson)
                .toList();

        JsonNode info = logs.stream().filter(log -> PROBE_MESSAGE.equals(log.path("message").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(output));
        assertThat(info.path("@timestamp").asText()).isNotBlank();
        assertThat(info.path("level").asText()).isEqualTo("INFO");
        assertThat(info.path("logger_name").asText()).isEqualTo("logging-profile-probe");
        assertThat(info.path("environment").asText()).isEqualTo("prod");
        assertThat(info.path("requestId").asText()).isEqualTo(REQUEST_ID);
        assertThat(info.path("event").asText()).isEqualTo("logging.profile.probe");

        JsonNode error = logs.stream().filter(log -> "ERROR".equals(log.path("level").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(output));
        assertThat(error.path("requestId").asText()).isEqualTo(REQUEST_ID);
        assertThat(error.path("errorCode").asText()).isEqualTo("COMMON5001");
        assertThat(error.path("event").asText()).isNotBlank();
        assertThat(error.path("exceptionType").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(error.path("stackFrames").asText()).contains("LoggingProfileTest");
        JsonNode upstream = logs.stream()
                .filter(log -> "external_api.call.failed".equals(log.path("event").asText()))
                .findFirst().orElseThrow(() -> new AssertionError(output));
        assertThat(upstream.path("provider").asText()).isEqualTo("gemini");
        assertThat(upstream.path("operation").asText()).isEqualTo("generateContent");
        assertThat(upstream.path("failureType").asText()).isEqualTo("quota_or_rate_limit");
        assertThat(upstream.path("upstreamHttpStatus").asInt()).isEqualTo(429);
        assertThat(upstream.path("retryable").asBoolean()).isTrue();
        assertThat(upstream.path("errorCode").asText()).isEqualTo("PROBE_ERROR");
        assertThat(upstream.path("requestId").asText()).isEqualTo(REQUEST_ID);
        assertThat(output).doesNotContain("fake-access-token", "fake-service-key", "fake-cookie");
    }

    @Test
    void localKeepsTextConsoleLogging() throws Exception {
        String output = runProbe("local");
        String probe = output.lines().filter(line -> line.contains(PROBE_MESSAGE))
                .findFirst().orElseThrow(() -> new AssertionError(output));

        assertThat(probe).contains("INFO", PROBE_MESSAGE).doesNotStartWith("{");
        assertThat(output).doesNotContain("fake-access-token", "fake-service-key", "fake-cookie");
    }

    private JsonNode readJson(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (Exception failure) {
            throw new AssertionError("JSON 로그 파싱 실패", failure);
        }
    }

    private String runProbe(String profile) throws Exception {
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", runtimeClasspath(), Probe.class.getName(), profile)
                .redirectErrorStream(true)
                .start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("로그 프로필 검증 프로세스 종료 여부").isTrue();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            return output;
        } finally {
            process.destroyForcibly();
        }
    }

    private String runtimeClasspath() throws Exception {
        Set<String> entries = new LinkedHashSet<>(Arrays.asList(
                System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) {
                for (var url : urls.getURLs()) {
                    entries.add(Path.of(url.toURI()).toString());
                }
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    @Configuration(proxyBeanMethods = false)
    public static class Probe {

        public static void main(String[] args) {
            SpringApplication application = new SpringApplication(Probe.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setLogStartupInfo(false);
            // 전체 애플리케이션 기동 없이 실제 환경별 로깅 설정 검증
            try (var context = application.run(
                    "--spring.profiles.active=" + args[0],
                    "--spring.config.location=classpath:/application-" + args[0] + ".yml",
                    "--spring.output.ansi.enabled=never")) {
                MDC.put("requestId", REQUEST_ID);
                try {
                    LoggerFactory.getLogger(PROBE_MESSAGE).atInfo()
                            .addKeyValue("event", "logging.profile.probe")
                            .log(PROBE_MESSAGE);
                    var failure = new IllegalStateException("Authorization: Bearer fake-access-token",
                            new RuntimeException("https://example.com?serviceKey=fake-service-key"));
                    failure.addSuppressed(new RuntimeException("Cookie: fake-cookie"));
                    new GlobalExceptionHandler().handleException(failure);
                    LoggerFactory.getLogger(PROBE_MESSAGE).atWarn()
                            .addKeyValue("event", "external_api.call.failed")
                            .addKeyValue("provider", "gemini")
                            .addKeyValue("operation", "generateContent")
                            .addKeyValue("failureType", "quota_or_rate_limit")
                            .addKeyValue("upstreamHttpStatus", 429)
                            .addKeyValue("retryable", true)
                            .addKeyValue("errorCode", "PROBE_ERROR")
                            .log("External API call failed");
                } finally {
                    MDC.clear();
                }
            }
        }
    }
}
