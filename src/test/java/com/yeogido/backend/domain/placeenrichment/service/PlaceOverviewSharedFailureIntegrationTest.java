package com.yeogido.backend.domain.placeenrichment.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.client.PlaceOverviewApiScope;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.entity.PlaceOverviewApiOperation;
import com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.repository.PlaceOverviewApiOperationRepository;
import com.yeogido.backend.domain.placeenrichment.scheduler.PlaceOverviewPendingScheduler;
import com.yeogido.backend.domain.placeenrichment.scheduler.PlaceOverviewRetryScheduler;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.config.JpaConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@ActiveProfiles("test")
@Import({JpaConfig.class, PlaceOverviewEnrichmentServiceImpl.class, PlaceOverviewApiGate.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlaceOverviewSharedFailureIntegrationTest {
  @Autowired PlaceOverviewEnrichmentService service;
  @Autowired PlaceOverviewApiGate gate;
  @Autowired PlaceRepository places;
  @Autowired RegionRepository regions;
  @Autowired PlaceOverviewApiOperationRepository operations;
  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean TourPlaceEnrichmentService tour;
  @MockitoBean GeminiPlaceEnrichmentService gemini;

  @Test
  void quotaStopsFollowingPlacesWithoutSpendingBudgetAndResumesAfterRecovery() {
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    var region = regions.saveAndFlush(Region.builder().name("서울").type(RegionType.REGION).build());
    Long first = save(region, "first");
    Long second = save(region, "second");
    var failure = new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
        "tour", "/searchKeyword2", ExternalApiFailureKind.DAILY_QUOTA, 429, "22", null);
    when(tour.enrich(any())).thenAnswer(invocation -> PlaceOverviewApiScope.call(
        "tour", "/searchKeyword2", () -> { throw failure; }));
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    var logs = new ListAppender<ILoggingEvent>() {
      @Override protected void append(ILoggingEvent event) {
        event.prepareForDeferredProcessing();
        super.append(event);
      }
    };
    logs.start();
    root.addAppender(logs);
    try {
      new PlaceOverviewPendingScheduler(places, service).enrichPendingOverviews();

      var waiting = places.findById(first).orElseThrow();
      var pending = places.findById(second).orElseThrow();
      assertThat(waiting.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.RETRY_WAIT);
      assertThat(waiting.getOverviewAttemptCount()).isZero();
      assertThat(waiting.getOverviewNextRetryAt()).isEqualTo(
          operations.findById("tour:/searchKeyword2").orElseThrow().getBlockedUntil());
      assertThat(pending.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.PENDING);
      assertThat(pending.getOverviewAttemptCount()).isZero();
      assertThat(pending.getOverviewAttemptedAt()).isNull();
      verify(tour, times(1)).enrich(any());
      verifyNoInteractions(gemini);

      var summary = logs.list.stream().filter(event ->
          "scheduler.execution.completed".equals(field(event, "event"))).findFirst().orElseThrow();
      var decision = logs.list.stream().filter(event ->
          "place.overview.retry.decided".equals(field(event, "event"))).findFirst().orElseThrow();
      assertThat(field(summary, "deferredCount")).isEqualTo(1L);
      assertThat(field(decision, "placeId")).isEqualTo(first);
      assertThat(field(decision, "stage")).isEqualTo("lookup");
      assertThat(field(decision, "provider")).isEqualTo("tour");
      assertThat(field(decision, "operation")).isEqualTo("/searchKeyword2");
      assertThat(field(decision, "upstreamHttpStatus")).isEqualTo(429);
      assertThat(field(decision, "providerCode")).isEqualTo("22");
      assertThat(field(decision, "retryDecision")).isEqualTo("wait");
      assertThat(decision.getMDCPropertyMap().get("executionId"))
          .isNotBlank().isEqualTo(summary.getMDCPropertyMap().get("executionId"));
      assertThat(decision.getThrowableProxy()).isNull();

      gate.release("tour", "/searchKeyword2");
      // 명시적 조기 복구 시 확인된 대기 항목만 선택적 재개
      jdbc.update("UPDATE \"place\" SET \"overview_next_retry_at\"=CURRENT_TIMESTAMP WHERE \"id\"=?", first);
      doReturn(new TourPlaceResponse.MatchResult("external", TourPlaceMatchStatus.MATCHED,
          null, null, "복구된 설명", 1, false, List.of())).when(tour).enrich(any());
      new PlaceOverviewRetryScheduler(places, service).retryFailedOverviews();
      new PlaceOverviewPendingScheduler(places, service).enrichPendingOverviews();
      for (Long id : List.of(first, second)) {
        var completed = places.findById(id).orElseThrow();
        assertThat(completed.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
        assertThat(completed.getOverviewAttemptCount()).isEqualTo(1);
      }
    } finally {
      root.detachAppender(logs);
      logs.stop();
    }
  }

  private Long save(Region region, String externalId) {
    return places.saveAndFlush(Place.builder().region(region).source(PlaceSource.KAKAO)
        .externalPlaceId(externalId).name(externalId).latitude(BigDecimal.valueOf(37))
        .longitude(BigDecimal.valueOf(127)).build()).getId();
  }

  private Object field(ILoggingEvent event, String name) {
    if (event.getKeyValuePairs() == null) return null;
    return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(name))
        .map(pair -> pair.value).findFirst().orElse(null);
  }
}
