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
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @MockitoBean TourPlaceEnrichmentService tour;
  @MockitoBean GeminiPlaceEnrichmentService gemini;

  @org.junit.jupiter.api.AfterEach
  void cleanUp() {
    places.deleteAll();
    regions.deleteAll();
    operations.deleteAll();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
      "DAILY_QUOTA,429,22", "RATE_LIMIT,200,23", "AUTHENTICATION,200,30", "CONFIGURATION,402,"
  })
  void sharedFailuresStopFollowingPlacesWithoutSpendingBudgetAndResumeAfterRecovery(
      ExternalApiFailureKind kind, int httpStatus, String providerCode) {
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    var region = regions.saveAndFlush(Region.builder().name("서울").type(RegionType.REGION).build());
    Long first = save(region, "first");
    Long second = save(region, "second");
    var failure = new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
        "tour", "/searchKeyword2", kind, httpStatus, providerCode, null);
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
      if (kind == ExternalApiFailureKind.AUTHENTICATION || kind == ExternalApiFailureKind.CONFIGURATION) {
        assertThat(waiting.getOverviewNextRetryAt()).isNull();
      } else {
        assertThat(waiting.getOverviewNextRetryAt()).isEqualTo(
            operations.findById("tour:/searchKeyword2").orElseThrow().getBlockedUntil());
      }
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
      assertThat(field(decision, "upstreamHttpStatus")).isEqualTo(httpStatus);
      assertThat(field(decision, "providerCode")).isEqualTo(providerCode);
      assertThat(field(decision, "retryDecision")).isEqualTo("wait");
      assertThat(decision.getMDCPropertyMap().get("executionId"))
          .isNotBlank().isEqualTo(summary.getMDCPropertyMap().get("executionId"));
      assertThat(decision.getThrowableProxy()).isNull();

      if (waiting.getOverviewNextRetryAt() == null) {
        assertThat(waiting.getOverviewBlockedByOperation()).isEqualTo("tour:/searchKeyword2");
      } else {
        assertThat(waiting.getOverviewBlockedByOperation()).isNull();
      }
      // 장소 예약 직접 수정 없이 해제 후 5분 경과 상황을 재현하기 위한 고정 시계 사용
      var releasedAt = java.time.LocalDateTime.now().minusMinutes(10).withNano(0);
      releaseGateAt(releasedAt).release("tour", "/searchKeyword2");
      if (kind == ExternalApiFailureKind.DAILY_QUOTA || kind == ExternalApiFailureKind.RATE_LIMIT) {
        assertThat(places.findById(first).orElseThrow().getOverviewNextRetryAt())
            .isEqualTo(waiting.getOverviewNextRetryAt());
        new PlaceOverviewRetryScheduler(places, service).retryFailedOverviews();
        verify(tour, times(1)).enrich(any());
        return;
      }
      assertThat(places.findById(first).orElseThrow().getOverviewNextRetryAt())
          .isEqualTo(releasedAt.plusMinutes(5));
      doReturn(new TourPlaceResponse.MatchResult("external", TourPlaceMatchStatus.MATCHED,
          null, null, "복구된 설명", 1, false, List.of())).when(tour).enrich(any());
      new PlaceOverviewRetryScheduler(places, service).retryFailedOverviews();
      new PlaceOverviewPendingScheduler(places, service).enrichPendingOverviews();
      for (Long id : List.of(first, second)) {
        var completed = places.findById(id).orElseThrow();
        assertThat(completed.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
        assertThat(completed.getOverviewAttemptCount()).isEqualTo(1);
        assertThat(completed.getOverviewBlockedByOperation()).isNull();
      }
    } finally {
      root.detachAppender(logs);
      logs.stop();
    }
  }

  @org.junit.jupiter.api.Test
  void releaseOnlyReschedulesManualWaitsForItsOwnOperation() {
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    var region = regions.saveAndFlush(Region.builder().name("서울").type(RegionType.REGION).build());
    java.time.LocalDateTime manual = null;
    var later = java.time.LocalDateTime.now().plusHours(2).withNano(0);
    Long own = waiting(region, "own", manual, "tour:/searchKeyword2");
    Long otherProvider = waiting(region, "other-provider", manual, "gemini:generateContent");
    Long otherOperation = waiting(region, "other-operation", manual, "tour:/detailCommon2");
    Long timed = waiting(region, "timed", later, "tour:/searchKeyword2");
    Long ordinary = waiting(region, "ordinary", later, null);

    var releasedAt = java.time.LocalDateTime.now().withNano(0);
    releaseGateAt(releasedAt).release("tour", "/searchKeyword2");

    var resumed = places.findById(own).orElseThrow();
    assertThat(resumed.getOverviewNextRetryAt()).isEqualTo(releasedAt.plusMinutes(5));
    assertThat(resumed.canStartOverviewEnrichment(releasedAt.plusMinutes(4))).isFalse();
    assertThat(resumed.canStartOverviewEnrichment(releasedAt.plusMinutes(5))).isTrue();
    assertThat(resumed.getOverviewAttemptCount()).isZero();
    assertThat(resumed.getOverviewBlockedByOperation()).isNull();
    for (Long id : List.of(otherProvider, otherOperation)) {
      assertThat(places.findById(id).orElseThrow().getOverviewNextRetryAt()).isEqualTo(manual);
    }
    for (Long id : List.of(timed, ordinary)) {
      assertThat(places.findById(id).orElseThrow().getOverviewNextRetryAt()).isEqualTo(later);
    }
  }

  @org.junit.jupiter.api.Test
  void releaseBeforeFailureIsSavedDoesNotLeaveAnInfiniteWait() {
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    var region = regions.saveAndFlush(Region.builder().name("서울").type(RegionType.REGION).build());
    Long id = save(region, "racing-release");
    var failure = new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
        "tour", "/searchKeyword2", ExternalApiFailureKind.AUTHENTICATION, 401, null, null);
    when(tour.enrich(any())).thenAnswer(invocation -> {
      gate.recordFailure(failure);
      gate.release("tour", "/searchKeyword2");
      throw failure;
    });
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enrich(id)).isSameAs(failure);
    var waiting = places.findById(id).orElseThrow();
    assertThat(waiting.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.RETRY_WAIT);
    assertThat(waiting.getOverviewAttemptCount()).isZero();
    assertThat(waiting.getOverviewNextRetryAt()).isAfter(java.time.LocalDateTime.now())
        .isBefore(java.time.LocalDateTime.now().plusMinutes(6));
    assertThat(waiting.getOverviewNextRetryAt()).isAfter(java.time.LocalDateTime.now().plusMinutes(4));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.EnumSource(value = ExternalApiFailureKind.class,
      names = {"RATE_LIMIT", "DAILY_QUOTA"})
  void releaseBeforeQuotaFailureIsSavedPreservesRetryAfter(ExternalApiFailureKind kind) {
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    var region = regions.saveAndFlush(Region.builder().name("서울").type(RegionType.REGION).build());
    Long id = save(region, "quota-release-race");
    var minimumRetryAt = java.time.LocalDateTime.now().plusHours(2);
    var failure = new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
        "tour", "/searchKeyword2", kind, 429, null, java.time.Duration.ofHours(2));
    when(tour.enrich(any())).thenAnswer(invocation -> {
      gate.recordFailure(failure);
      gate.release("tour", "/searchKeyword2");
      throw failure;
    });
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enrich(id)).isSameAs(failure);
    var waiting = places.findById(id).orElseThrow();
    assertThat(waiting.getOverviewNextRetryAt()).isAfterOrEqualTo(minimumRetryAt);
    assertThat(waiting.getOverviewAttemptCount()).isZero();
    new PlaceOverviewRetryScheduler(places, service).retryFailedOverviews();
    verify(tour, times(1)).enrich(any());
  }

  @org.junit.jupiter.api.Test
  void excessiveQuotaDelayIsCappedForBothFailureAndDeferredPlaces() {
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    operations.saveAndFlush(new PlaceOverviewApiOperation("daum", "searchWeb"));
    var region = regions.saveAndFlush(Region.builder().name("서울").type(RegionType.REGION).build());
    Long first = save(region, "maximum-quota");
    Long second = save(region, "maximum-deferred");
    var earliestRetryAt = java.time.LocalDateTime.now().plusDays(7);
    var failure = new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR,
        "daum", "searchWeb", ExternalApiFailureKind.RATE_LIMIT, 429, null,
        java.time.Duration.ofSeconds(Long.MAX_VALUE));
    when(tour.enrich(any())).thenAnswer(invocation -> PlaceOverviewApiScope.call(
        "daum", "searchWeb", () -> { throw failure; }));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enrich(first)).isSameAs(failure);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.enrich(second))
        .isInstanceOf(com.yeogido.backend.domain.placeenrichment.exception.ExternalApiDeferredException.class);
    for (Long id : List.of(first, second)) {
      assertThat(places.findById(id).orElseThrow().getOverviewNextRetryAt())
          .isBetween(earliestRetryAt, java.time.LocalDateTime.now().plusDays(7));
    }
    gate.release("daum", "searchWeb");
    for (Long id : List.of(first, second)) {
      assertThat(places.findById(id).orElseThrow().getOverviewNextRetryAt())
          .isBetween(earliestRetryAt, java.time.LocalDateTime.now().plusDays(7));
    }
  }

  private PlaceOverviewApiGate releaseGateAt(java.time.LocalDateTime releasedAt) {
    var zone = java.time.ZoneId.systemDefault();
    return new PlaceOverviewApiGate(operations, places, transactionManager, 86400, 60, 30,
        java.time.Clock.fixed(releasedAt.atZone(zone).toInstant(), zone));
  }

  private Long waiting(Region region, String name, java.time.LocalDateTime at, String operation) {
    Place place = places.findById(save(region, name)).orElseThrow();
    place.startOverviewEnrichment(java.time.LocalDateTime.now());
    if (operation == null) {
      place.waitForOverviewRetry(at, false);
    } else {
      place.waitForOverviewApi(at, operation);
    }
    return places.saveAndFlush(place).getId();
  }

  private Long save(Region region, String externalId) {
    return places.saveAndFlush(Place.builder().region(region).source(PlaceSource.KAKAO)
        .externalPlaceId(externalId).name(externalId).latitude(BigDecimal.valueOf(37))
        .longitude(BigDecimal.valueOf(127)).build()).getId();
  }

  private Object field(ILoggingEvent event, String name) {
    if (event.getKeyValuePairs() == null) return null;
    return event.getKeyValuePairs().stream().filter(pair -> pair.key.equals(name))
        .findFirst().map(pair -> pair.value).orElse(null);
  }
}
