package com.yeogido.backend.domain.content.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.course.client.CourseSearchClient;
import com.yeogido.backend.domain.course.dto.request.CourseReqDTO.CourseListReq;
import com.yeogido.backend.domain.course.exception.CourseErrorCode;
import com.yeogido.backend.domain.course.service.CourseSearchServiceImpl;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.service.AdminAuthorizationService;
import com.yeogido.backend.global.exception.GeneralException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ExternalApiOutcomeLoggingTest {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private Map<String, String> previousContext;

    @BeforeEach
    void captureLogs() {
        previousContext = MDC.getCopyOfContextMap();
        MDC.put("requestId", "outcome-request-id");
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void restoreContext() {
        root.detachAppender(logs);
        logs.stop();
        if (previousContext == null) MDC.clear(); else MDC.setContextMap(previousContext);
    }

    @Test
    void detailFailureIsCountedOnceAndFallbackSelectionHasSeparateEvent() {
        RestClient.Builder builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new TourApiClient(builder);
        ReflectionTestUtils.setField(client, "baseUrl", "https://provider.test");
        ReflectionTestUtils.setField(client, "serviceKey", "fake-private-api-key");
        var service = new TourContentSyncServiceImpl(client, mock(ContentRepository.class),
                mock(ContentExternalLinkService.class), mock(PlaceRepository.class),
                mock(RegionRepository.class), mock(AdminAuthorizationService.class),
                mock(PlatformTransactionManager.class));
        server.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("fake-private-response Authorization: Bearer fake-private-token"));

        assertThat((Object) ReflectionTestUtils.invokeMethod(service, "fetchDetail", "fake-private-content-id"))
                .isNull();

        var structured = logs.list.stream().filter(event -> event.getKeyValuePairs() != null).toList();
        assertThat(structured).hasSize(2);
        assertThat(structured).filteredOn(event -> "external_api.call.failed".equals(field(event, "event")))
                .hasSize(1);
        assertThat(structured).filteredOn(event -> "external_api.fallback.selected".equals(field(event, "event")))
                .singleElement().satisfies(event -> {
                    assertThat(field(event, "fallback")).isEqualTo("basic_content");
                    assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
                });
        assertThat(structured).noneSatisfy(event ->
                assertThat(field(event, "event")).isEqualTo("external_api.fallback.succeeded"));
        assertSafe(structured);
        server.verify();
    }

    @Test
    void interpretationFailureSelectsFallbackWithoutClaimingQuerySuccess() {
        var client = mock(CourseSearchClient.class);
        when(client.isEnabled()).thenReturn(true);
        when(client.search(anyString(), anyString()))
                .thenThrow(new GeneralException(CourseErrorCode.COURSE_SEARCH_API_ERROR));
        var service = new CourseSearchServiceImpl(client, new ObjectMapper(), mock(RegionRepository.class));
        var request = new CourseListReq(null, "fake-private-search-query", null,
                null, null, null, null, null, null, null, null, null, 10);

        assertThat(service.interpret(request)).isNull();

        var structured = logs.list.stream().filter(event -> event.getKeyValuePairs() != null).toList();
        assertThat(structured).singleElement().satisfies(event -> {
            assertThat(field(event, "event")).isEqualTo("external_api.fallback.selected");
            assertThat(field(event, "fallback")).isEqualTo("literal_search");
            assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO);
        });
        assertSafe(structured);
    }

    private Object field(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(pair -> key.equals(pair.key))
                .map(pair -> pair.value).findFirst().orElse(null);
    }

    private void assertSafe(java.util.List<ILoggingEvent> events) {
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getThrowableProxy()).isNull();
            assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "outcome-request-id");
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs())
                    .doesNotContain("fake-private", "Authorization:", "Bearer ", "serviceKey=");
        });
    }
}
