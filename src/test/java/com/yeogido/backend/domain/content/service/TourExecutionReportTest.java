package com.yeogido.backend.domain.content.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.content.entity.Content;
import com.yeogido.backend.domain.content.repository.ContentRepository;
import com.yeogido.backend.domain.content.scheduler.TourContentSyncScheduler;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.domain.user.service.AdminAuthorizationService;
import com.yeogido.backend.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourExecutionReportTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TourApiClient client = mock(TourApiClient.class);
    private final ContentRepository contents = mock(ContentRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final RegionRepository regions = mock(RegionRepository.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    @Test
    void actualProcessingSeparatesFilteringItemFailuresAndDetailFallbacks() {
        var content = mock(Content.class);
        var parent = mock(Region.class);
        var child = mock(Region.class);
        when(contents.findBySourceAndExternalContentId(any(), eq("123"))).thenReturn(Optional.of(content));
        when(contents.findBySourceAndExternalContentId(any(), eq("456")))
                .thenThrow(new IllegalStateException("fake-private-response"));
        when(client.detailCommon("123")).thenThrow(new IllegalStateException("serviceKey=fake-private-key"));
        when(regions.findByFullName("서울")).thenReturn(Optional.of(parent));
        when(regions.findByParentAndName(parent, "중구")).thenReturn(Optional.of(child));
        when(places.findBySourceAndExternalPlaceId(any(), eq("123")))
                .thenReturn(Optional.of(mock(Place.class)));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        JsonNode response = mapper.createObjectNode();
        when(client.searchFestivals(any(), any(), anyInt(), anyInt())).thenReturn(response);
        when(client.items(response)).thenReturn(List.of(item("123"), item("456"), mapper.createObjectNode()));
        when(client.totalCount(response)).thenReturn(3);
        var service = service();

        var report = service.synchronizeWithReport();

        assertThat(report.result().receivedCount()).isEqualTo(3);
        assertThat(report.result().updatedCount()).isEqualTo(1);
        assertThat(report.result().createdCount()).isZero();
        assertThat(report.result().skippedCount()).isEqualTo(2);
        assertThat(report.failedItemCount()).isEqualTo(1);
        assertThat(report.detailFailureCount()).isEqualTo(1);
        verify(transactions).commit(any());
    }

    @Test
    void allItemFailureKeepsOriginalExceptionContractAndSafeExecutionLog() {
        when(contents.findBySourceAndExternalContentId(any(), any()))
                .thenThrow(new IllegalStateException("Authorization: fake-private-token"));
        JsonNode response = mapper.createObjectNode();
        when(client.searchFestivals(any(), any(), anyInt(), anyInt())).thenReturn(response);
        when(client.items(response)).thenReturn(List.of(item("fake-private-content-id")));
        when(client.totalCount(response)).thenReturn(1);
        var syncService = service();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var logs = new ListAppender<ILoggingEvent>() {
            @Override protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        var previous = MDC.getCopyOfContextMap();
        logs.start();
        root.addAppender(logs);
        try {
            assertThatThrownBy(() -> new TourContentSyncScheduler(syncService).synchronizeTourContents())
                    .isInstanceOfSatisfying(GeneralException.class,
                            failure -> assertThat(failure.getErrorCode().getCode()).isEqualTo("CONTENT5001"));
            var summaries = logs.list.stream().filter(event -> event.getKeyValuePairs() != null
                    && event.getKeyValuePairs().stream().anyMatch(pair -> pair.key.equals("event")
                    && pair.value.equals("scheduler.execution.completed"))).toList();
            assertThat(summaries).hasSize(1);
            String id = summaries.getFirst().getMDCPropertyMap().get("executionId");
            assertThat(logs.list).allSatisfy(event -> {
                assertThat(event.getMDCPropertyMap()).containsEntry("executionId", id);
                assertThat(event.getFormattedMessage() + event.getKeyValuePairs()).doesNotContain("fake-private");
                assertThat(event.getThrowableProxy()).isNull();
            });
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(previous);
        } finally {
            root.detachAppender(logs);
            logs.stop();
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }

    private JsonNode item(String id) {
        return mapper.createObjectNode().put("contentid", id).put("title", "fake-private-title")
                .put("addr1", "서울 중구 fake-private-address").put("mapy", "37.5").put("mapx", "127.0");
    }

    private TourContentSyncServiceImpl service() {
        var service = new TourContentSyncServiceImpl(client, contents, mock(ContentExternalLinkService.class),
                places, regions, mock(AdminAuthorizationService.class), transactions);
        ReflectionTestUtils.setField(service, "syncMonths", 12);
        ReflectionTestUtils.setField(service, "pageSize", 100);
        ReflectionTestUtils.setField(service, "maxPages", 1);
        ReflectionTestUtils.setField(service, "maxDetailCalls", 10);
        return service;
    }
}
