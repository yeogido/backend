package com.yeogido.backend.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.auth.service.JwtTokenProvider;
import com.yeogido.backend.global.exception.GlobalExceptionHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RequestIdIntegrationTest.RequestLoggingConfiguration.class)
class RequestIdIntegrationTest {

  private static final String HEADER = "X-Request-ID";
  private static final String BASE_PATH = "/api/v1/regions/request-id-test";
  private static final String SECRET = "fake-private-api-key";

  @Autowired private MockMvc mockMvc;
  @MockitoSpyBean private JwtTokenProvider jwtTokenProvider;

  private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(RequestLoggingService.class);
  private final Logger errorLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final ListAppender<ILoggingEvent> serviceLogs = capturingAppender();
  private final ListAppender<ILoggingEvent> errorLogs = capturingAppender();
  private Map<String, String> originalContext;

  @BeforeEach
  void attachAppenders() {
    originalContext = MDC.getCopyOfContextMap();
    MDC.clear();
    serviceLogs.start();
    errorLogs.start();
    serviceLogger.addAppender(serviceLogs);
    errorLogger.addAppender(errorLogs);
  }

  @AfterEach
  void detachAppenders() {
    serviceLogger.detachAppender(serviceLogs);
    errorLogger.detachAppender(errorLogs);
    serviceLogs.stop();
    errorLogs.stop();
    MDC.clear();
    if (originalContext != null) {
      MDC.setContextMap(originalContext);
    }
  }

  @Test
  void serviceLogsShareGeneratedResponseRequestIdWithoutChangingResponse() throws Exception {
    MvcResult result = mockMvc.perform(get(BASE_PATH).header(HEADER, "untrusted-client-id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("value").value("unchanged"))
        .andReturn();

    String requestId = requestId(result);
    assertThat(requestId).isNotEqualTo("untrusted-client-id");
    assertThat(serviceLogs.list).hasSize(2).allSatisfy(event ->
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", requestId));
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

    MvcResult next = mockMvc.perform(get(BASE_PATH)).andExpect(status().isOk()).andReturn();
    assertThat(requestId(next)).isNotEqualTo(requestId);
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void globalErrorKeepsContractAndSafeStructuredFieldsWithRequestId() throws Exception {
    MvcResult result = mockMvc.perform(get(BASE_PATH + "/failure").param("serviceKey", SECRET))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("isSuccess").value(false))
        .andExpect(jsonPath("code").value("COMMON5001"))
        .andExpect(jsonPath("message").value("서버 내부 오류가 발생했습니다."))
        .andReturn();

    assertThat(errorLogs.list).hasSize(1);
    ILoggingEvent event = errorLogs.list.getFirst();
    assertThat(event.getMDCPropertyMap()).containsEntry("requestId", requestId(result));
    assertThat(event.getKeyValuePairs()).anySatisfy(pair -> {
      assertThat(pair.key).isEqualTo("event");
      assertThat(pair.value).isEqualTo("api.request.failed");
    }).anySatisfy(pair -> {
      assertThat(pair.key).isEqualTo("errorCode");
      assertThat(pair.value).isEqualTo("COMMON5001");
    });
    assertThat(event.getThrowableProxy()).isNull();
    assertThat(event.getFormattedMessage()).doesNotContain(SECRET, "private-access-token");
    assertThat(event.getKeyValuePairs().toString()).doesNotContain(SECRET, "private-access-token");
    assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET);
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void missingAuthenticationKeepsErrorContractAndRequestIdHeader() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("code").value("AUTH4011"))
        .andExpect(jsonPath("message").value("로그인이 필요합니다."))
        .andReturn();

    requestId(result);
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void invalidTokenIsProcessedInsideRequestContextWithoutTokenLogging() throws Exception {
    AtomicReference<String> authenticationRequestId = new AtomicReference<>();
    doAnswer(invocation -> {
      authenticationRequestId.set(MDC.get("requestId"));
      return invocation.callRealMethod();
    }).when(jwtTokenProvider).parseAccessToken(anyString());

    MvcResult result = mockMvc.perform(get("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer private-access-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("code").value("AUTH4011"))
        .andExpect(jsonPath("message").value("로그인이 필요합니다."))
        .andReturn();

    assertThat(authenticationRequestId.get()).isEqualTo(requestId(result));
    assertThat(errorLogs.list).isEmpty();
    assertThat(result.getResponse().getContentAsString()).doesNotContain("private-access-token");
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  void corsExposesRequestIdAlongsideExistingEtagHeader() throws Exception {
    MvcResult result = mockMvc.perform(get(BASE_PATH).header(HttpHeaders.ORIGIN, "https://yeogido.kr"))
        .andExpect(status().isOk()).andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
        .contains("ETag", HEADER);
    assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .isEqualTo("https://yeogido.kr");
    requestId(result);
  }

  private static String requestId(MvcResult result) {
    String value = result.getResponse().getHeader(HEADER);
    assertThat(value).isNotNull();
    assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    return value;
  }

  private static ListAppender<ILoggingEvent> capturingAppender() {
    return new ListAppender<>() {
      @Override
      protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        super.append(event);
      }
    };
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RequestLoggingConfiguration {
    @Bean
    RequestLoggingService requestLoggingService() {
      return new RequestLoggingService();
    }

    @Bean
    RequestLoggingController requestLoggingController(RequestLoggingService service) {
      return new RequestLoggingController(service);
    }
  }

  static class RequestLoggingService {
    void execute() {
      LoggerFactory.getLogger(RequestLoggingService.class).info("Request test operation started");
      LoggerFactory.getLogger(RequestLoggingService.class).info("Request test operation completed");
    }
  }

  @RestController
  static class RequestLoggingController {
    private final RequestLoggingService service;

    RequestLoggingController(RequestLoggingService service) {
      this.service = service;
    }

    @GetMapping(BASE_PATH)
    Map<String, String> success() {
      service.execute();
      return Map.of("value", "unchanged");
    }

    @GetMapping(BASE_PATH + "/failure")
    void failure() {
      throw new IllegalStateException("https://provider.test?serviceKey=" + SECRET,
          new RuntimeException("Authorization: Bearer private-access-token"));
    }
  }
}
