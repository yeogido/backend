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
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

    doReturn(tourResult(TourPlaceMatchStatus.MATCHED, "재시도 설명")).when(tour).enrich(any());
    service.enrich(placeId);
    assertThat(places.findById(placeId).orElseThrow().getOverviewStatus())
        .isEqualTo(PlaceOverviewStatus.COMPLETED);
  }

  @Test
  void finalCommitFailureIsPersistedAndAllowsRetry() {
    var failure = new org.springframework.dao.DataAccessResourceFailureException("최종 저장 실패");
    when(tour.enrich(any())).thenReturn(tourResult(TourPlaceMatchStatus.MATCHED, "저장할 설명"));
    var commits = new java.util.concurrent.atomic.AtomicInteger();
    // 최종 저장 트랜잭션에서만 커밋 직전 장애를 주입해 실제 롤백을 검증한다.
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

    service.enrich(placeId);
    var saved = places.findById(placeId).orElseThrow();
    assertThat(saved.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.COMPLETED);
    assertThat(saved.getOverview()).isEqualTo("저장할 설명");
    verify(tour, times(2)).enrich(any());
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
  void processingPlaceAndMissingPlaceDoNotTriggerExternalCalls() {
    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
        places.findById(placeId).orElseThrow().startOverviewEnrichment(java.time.LocalDateTime.now()));
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

  private TourPlaceResponse.MatchResult tourResult(TourPlaceMatchStatus status, String overview) {
    return new TourPlaceResponse.MatchResult("external-id", status, null, null, overview, 1, false, List.of());
  }

  private GeminiPlaceResponse.Result geminiResult(GeminiPlaceMatchStatus status, String overview) {
    return new GeminiPlaceResponse.Result("external-id", "테스트 장소", status, "reason", overview,
        List.of(), List.of(), List.of(), null, List.of());
  }
}
