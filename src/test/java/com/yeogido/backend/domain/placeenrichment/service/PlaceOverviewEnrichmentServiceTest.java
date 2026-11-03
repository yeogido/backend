package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.place.entity.Place;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeogido.backend.domain.content.client.TourApiClient;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.enums.GeminiPlaceMatchStatus;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;
import com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus;
import com.yeogido.backend.domain.placeenrichment.scheduler.PlaceOverviewRetryScheduler;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({JpaConfig.class, PlaceOverviewEnrichmentServiceImpl.class, TourRegionCodeMapper.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlaceOverviewEnrichmentServiceTest {

  @Autowired PlaceOverviewEnrichmentService service;
  @Autowired PlaceRepository places;
  @Autowired RegionRepository regions;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @MockitoSpyBean PlatformTransactionManager transactionManager;
  @Autowired TourRegionCodeMapper regionCodeMapper;
  @MockitoBean TourPlaceEnrichmentService tour;
  @MockitoBean GeminiPlaceEnrichmentService gemini;
  @MockitoBean TourApiClient tourApiClient;

  private Long placeId;

  @BeforeEach
  void setUp() {
    var region = regions.saveAndFlush(Region.builder().name("부산")
        .type(RegionType.REGION).build());
    placeId = places.saveAndFlush(Place.builder().region(region).source(PlaceSource.KAKAO)
        .externalPlaceId(UUID.randomUUID().toString()).name("테스트 장소")
        .roadAddress("부산광역시 중구 테스트로 1")
        .latitude(new BigDecimal("35.1000000"))
        .longitude(new BigDecimal("129.0000000")).build()).getId();
  }

  @Test
  void savesTourOverviewOutsideCallerTransactionAndSkipsRepeatedRequests() {
    when(tour.enrich(any())).thenAnswer(invocation -> {
      assertExternalCallState();
      return tourResult(TourPlaceMatchStatus.MATCHED, "관광공사 설명");
    });
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      service.enrich(placeId);
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
      status.setRollbackOnly();
    });

    Place saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverview()).isEqualTo("관광공사 설명");
    assertThat(saved.getOverviewSource()).isEqualTo(PlaceOverviewSource.TOUR_API);
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(saved.getOverviewAttemptedAt()).isNotNull();
    service.enrich(placeId);
    verify(tour, times(1)).enrich(any());
    verifyNoInteractions(gemini);
  }

  @Test
  void fallsBackToVerifiedGeminiOverview() {
    when(tour.enrich(any())).thenReturn(tourResult(TourPlaceMatchStatus.UNMATCHED, null));
    when(gemini.enrich(any())).thenAnswer(invocation -> {
      assertExternalCallState();
      return geminiResult(GeminiPlaceMatchStatus.VERIFIED, "보완된 설명");
    });

    service.enrich(placeId);

    var saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverview()).isEqualTo("보완된 설명");
    assertThat(saved.getOverviewSource()).isEqualTo(PlaceOverviewSource.DAUM_GEMINI);
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
  }

  @ParameterizedTest
  @EnumSource(GeminiPlaceMatchStatus.class)
  void emptyOrUnverifiedGeminiResultsDoNotBecomeDescriptions(GeminiPlaceMatchStatus matchStatus) {
    when(tour.enrich(any())).thenReturn(tourResult(TourPlaceMatchStatus.AMBIGUOUS, "불확실한 설명"));
    when(gemini.enrich(any())).thenReturn(geminiResult(matchStatus,
        matchStatus == GeminiPlaceMatchStatus.VERIFIED ? " " : "검증되지 않은 설명"));

    service.enrich(placeId);

    var saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverview()).isNull();
    assertThat(saved.getOverviewSource()).isNull();
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.NO_OVERVIEW);
    service.enrich(placeId);
    verify(tour, times(1)).enrich(any());
  }

  @Test
  void persistsFailureAndAllowsRetry() {
    var failure = new IllegalStateException("외부 조회 실패");
    when(tour.enrich(any())).thenThrow(failure);
    assertThatThrownBy(() -> service.enrich(placeId)).isSameAs(failure);
    var failed = places.findById(placeId).orElseThrow();
    assertThat(failed.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.FAILED);
    assertThat(failed.getOverviewAttemptedAt()).isNotNull();
    verifyNoInteractions(gemini);

    assertThat(failed.getOverviewAttemptCount()).isEqualTo(1);
    service.enrich(placeId);
    verify(tour, times(1)).enrich(any());
    makeRetryDue();
    doReturn(tourResult(TourPlaceMatchStatus.MATCHED, "재시도 설명")).when(tour).enrich(any());
    new PlaceOverviewRetryScheduler(places, service).retryFailedOverviews();
    assertThat(places.findById(placeId).orElseThrow().getOverviewStatus())
        .isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(places.findById(placeId).orElseThrow().getOverviewAttemptCount()).isEqualTo(2);
  }

  @Test
  void finalCommitFailureIsPersistedAndAllowsRetry() {
    var failure = new org.springframework.dao.DataAccessResourceFailureException("최종 저장 실패");
    when(tour.enrich(any())).thenReturn(tourResult(TourPlaceMatchStatus.MATCHED, "저장할 설명"));
    var commits = new java.util.concurrent.atomic.AtomicInteger();
    // 최종 저장 트랜잭션의 커밋 직전 장애 주입 및 실제 롤백 검증
    doAnswer(invocation -> {
      if (commits.incrementAndGet() == 2) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
          @Override
          public void beforeCommit(boolean readOnly) {
            throw failure;
          }
        });
      }
      return invocation.callRealMethod();
    }).when(transactionManager).commit(any());

    assertThatThrownBy(() -> service.enrich(placeId)).isSameAs(failure);
    var failed = places.findById(placeId).orElseThrow();
    assertThat(failed.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.FAILED);
    assertThat(failed.getOverview()).isNull();
    assertThat(failed.getOverviewSource()).isNull();

    makeRetryDue();
    service.enrich(placeId);
    var saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(saved.getOverview()).isEqualTo("저장할 설명");
    verify(tour, times(2)).enrich(any());
  }

  @Test
  void stopsAfterOneRetryEvenWhenCalledAgain() {
    when(tour.enrich(any())).thenThrow(new IllegalStateException("외부 조회 실패"));
    assertThatThrownBy(() -> service.enrich(placeId)).isInstanceOf(IllegalStateException.class);
    makeRetryDue();
    assertThatThrownBy(() -> service.enrich(placeId)).isInstanceOf(IllegalStateException.class);

    var saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.FAILED);
    assertThat(saved.getOverviewAttemptCount()).isEqualTo(2);
    service.enrich(placeId);
    verify(tour, times(2)).enrich(any());
    assertThat(places.findOverviewRetryCandidates(LocalDateTime.now().minusMinutes(5),
        LocalDateTime.now().minusMinutes(Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES), 2,
        org.springframework.data.domain.PageRequest.of(0, 20))).doesNotContain(placeId);
  }

  @Test
  void concurrentRetryCallsOnlyRunOneExternalLookup() throws Exception {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      var place = places.findById(placeId).orElseThrow();
      place.startOverviewEnrichment(LocalDateTime.now().minusMinutes(6));
      place.failOverviewEnrichment();
    });
    var entered = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    when(tour.enrich(any())).thenAnswer(invocation -> {
      entered.countDown();
      assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      return tourResult(TourPlaceMatchStatus.MATCHED, "재시도 설명");
    });
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.enrich(placeId));
      try {
        assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> service.enrich(placeId));
        second.get(5, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
      first.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    var saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(saved.getOverviewAttemptCount()).isEqualTo(2);
    verify(tour, times(1)).enrich(any());
  }

  @Test
  void geminiFailureIsPersistedAsFailedInsteadOfNoOverview() {
    when(tour.enrich(any())).thenReturn(tourResult(TourPlaceMatchStatus.MATCHED, null));
    when(gemini.enrich(any())).thenThrow(new IllegalStateException("Gemini 실패"));
    assertThatThrownBy(() -> service.enrich(placeId)).isInstanceOf(IllegalStateException.class);
    assertThat(places.findById(placeId).orElseThrow().getOverviewStatus())
        .isEqualTo(PlaceOverviewStatus.FAILED);
  }

  @Test
  void staleOverviewCannotOverwriteNewAttemptAfterPlaceEdit() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    when(tour.enrich(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        editPlace();
        service.enrich(placeId);
        return tourResult(TourPlaceMatchStatus.MATCHED, "이전 장소 설명");
      }
      return tourResult(TourPlaceMatchStatus.MATCHED, "수정된 장소 설명");
    });
    service.enrich(placeId);
    var found = places.findById(placeId).orElseThrow();
    assertThat(found.getOverview()).isEqualTo("수정된 장소 설명");
    assertThat(found.getOverviewAttemptCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void staleSuccessOrFailureDoesNotOverrideEditedPendingPlace(boolean fails) {
    when(tour.enrich(any())).thenAnswer(invocation -> {
      editPlace();
      if (fails) throw new IllegalStateException("이전 조회 실패");
      return tourResult(TourPlaceMatchStatus.MATCHED, "이전 장소 설명");
    });
    if (fails) {
      assertThatThrownBy(() -> service.enrich(placeId)).isInstanceOf(IllegalStateException.class);
    } else {
      service.enrich(placeId);
    }
    var found = places.findById(placeId).orElseThrow();
    assertThat(found.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.PENDING);
    assertThat(found.getOverview()).isNull();
    assertThat(found.getOverviewAttemptCount()).isZero();
  }

  private void editPlace() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      var place = places.findById(placeId).orElseThrow();
      place.updateTourData(place.getRegion(), "수정된 장소", place.getRoadAddress(), place.getLotAddress(),
          place.getLatitude(), place.getLongitude());
    });
  }

  @Test
  void processingRecoveryBoundaryAndAttemptLimitAreInclusive() {
    var now = LocalDateTime.of(2026, 11, 2, 12, 0);
    var boundary = now.minusMinutes(Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES);
    var atBoundary = Place.builder().overviewStatus(PlaceOverviewStatus.PROCESSING)
        .overviewAttemptCount(1).overviewAttemptedAt(boundary).build();
    assertThat(atBoundary.canStartOverviewEnrichment(now)).isTrue();
    assertThat(atBoundary.canStartOverviewEnrichment(now.minusNanos(1))).isFalse();
    assertThat(Place.builder().overviewStatus(PlaceOverviewStatus.PROCESSING)
        .overviewAttemptCount(Place.MAX_OVERVIEW_ATTEMPTS).overviewAttemptedAt(boundary)
        .build().canStartOverviewEnrichment(now)).isFalse();
  }

  @Test
  void staleProcessingRetriesWithinAttemptLimit() {
    setProcessingAttempt(1, LocalDateTime.now().minusMinutes(
        Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES + 1));
    when(tour.enrich(any())).thenReturn(tourResult(TourPlaceMatchStatus.MATCHED, "복구된 설명"));

    service.enrich(placeId);

    var found = places.findById(placeId).orElseThrow();
    assertThat(found.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(found.getOverview()).isEqualTo("복구된 설명");
    assertThat(found.getOverviewAttemptCount()).isEqualTo(Place.MAX_OVERVIEW_ATTEMPTS);
    verify(tour).enrich(any());
  }

  @Test
  void staleFinalAttemptBecomesFailedWithoutAnotherExternalCall() {
    var attemptedAt = LocalDateTime.now().minusMinutes(
        Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES + 1).withNano(0);
    setProcessingAttempt(Place.MAX_OVERVIEW_ATTEMPTS, attemptedAt);

    service.enrich(placeId);
    service.enrich(placeId);

    var found = places.findById(placeId).orElseThrow();
    assertThat(found.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.FAILED);
    assertThat(found.getOverviewAttemptCount()).isEqualTo(Place.MAX_OVERVIEW_ATTEMPTS);
    assertThat(found.getOverviewAttemptedAt()).isEqualTo(attemptedAt);
    verifyNoInteractions(tour, gemini);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void lateResultOrFailureCannotOverwriteRecoveredAttempt(boolean fails) {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    when(tour.enrich(any())).thenAnswer(invocation -> {
      if (calls.incrementAndGet() == 1) {
        setProcessingAttempt(1, LocalDateTime.now().minusMinutes(
            Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES + 1));
        service.enrich(placeId);
        if (fails) throw new IllegalStateException("중단된 시도에서 늦게 도착한 실패");
        return tourResult(TourPlaceMatchStatus.MATCHED, "중단된 시도의 설명");
      }
      return tourResult(TourPlaceMatchStatus.MATCHED, "복구된 시도의 설명");
    });

    if (fails) {
      assertThatThrownBy(() -> service.enrich(placeId)).isInstanceOf(IllegalStateException.class);
    } else {
      service.enrich(placeId);
    }

    var found = places.findById(placeId).orElseThrow();
    assertThat(found.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(found.getOverview()).isEqualTo("복구된 시도의 설명");
    assertThat(found.getOverviewAttemptCount()).isEqualTo(Place.MAX_OVERVIEW_ATTEMPTS);
    verify(tour, times(2)).enrich(any());
  }

  private void setProcessingAttempt(int count, LocalDateTime attemptedAt) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        entityManager.createQuery("""
            update Place p set p.overviewStatus = :status,
              p.overviewAttemptCount = :count, p.overviewAttemptedAt = :attemptedAt
            where p.id = :id
            """).setParameter("status", PlaceOverviewStatus.PROCESSING)
            .setParameter("count", count).setParameter("attemptedAt", attemptedAt)
            .setParameter("id", placeId).executeUpdate());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2})
  void processingPlaceAndMissingPlaceDoNotTriggerExternalCalls(int attemptCount) {
    setProcessingAttempt(attemptCount, LocalDateTime.now());
    service.enrich(placeId);
    assertThatThrownBy(() -> service.enrich(Long.MAX_VALUE)).isInstanceOf(GeneralException.class);
    verifyNoInteractions(tour, gemini);
  }

  @Test
  void regionCodeLookupReadsLazyParentsAndCallsApiOutsideTransaction() throws Exception {
    var province = regions.saveAndFlush(Region.builder().name("부산광역시")
        .type(RegionType.REGION).build());
    var district = regions.saveAndFlush(Region.builder().name("중구").parent(province)
        .type(RegionType.REGION).build());
    var json = new ObjectMapper();
    var provincePage = json.readTree("""
        {"response":{"body":{"totalCount":1,"items":{"item":[{"code":"26","name":"부산광역시"}]}}}}
        """);
    var districtPage = json.readTree("""
        {"response":{"body":{"totalCount":1,"items":{"item":[{"code":"110","name":"중구"}]}}}}
        """);
    when(tourApiClient.items(any())).thenCallRealMethod();
    when(tourApiClient.totalCount(any())).thenCallRealMethod();
    when(tourApiClient.legalDistrictCodes(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return invocation.getArgument(0) == null ? provincePage : districtPage;
    });

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      assertThat(regionCodeMapper.resolve(Region.builder().id(district.getId()).build(), null, null))
          .contains(new com.yeogido.backend.domain.placeenrichment.dto.TourRegionCode("26", "110"));
      status.setRollbackOnly();
    });

    var saved = regions.findById(district.getId()).orElseThrow();
    assertThat(saved.getTourLdongRegnCd()).isEqualTo("26");
    assertThat(saved.getTourLdongSignguCd()).isEqualTo("110");
  }

  private void assertExternalCallState() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    var processing = places.findById(placeId).orElseThrow();
    assertThat(processing.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.PROCESSING);
    assertThat(processing.getOverviewAttemptedAt()).isNotNull();
  }

  private void makeRetryDue() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        entityManager.createQuery("update Place p set p.overviewAttemptedAt = :time where p.id = :id")
            .setParameter("time", LocalDateTime.now().minusMinutes(6))
            .setParameter("id", placeId).executeUpdate());
  }

  private TourPlaceResponse.MatchResult tourResult(TourPlaceMatchStatus status, String overview) {
    return new TourPlaceResponse.MatchResult("external-id", status, null, null, overview, 1, false, List.of());
  }

  private GeminiPlaceResponse.Result geminiResult(GeminiPlaceMatchStatus status, String overview) {
    return new GeminiPlaceResponse.Result("external-id", "테스트 장소", status, "reason", overview,
        List.of(), List.of(), List.of(), null, List.of());
  }
}
