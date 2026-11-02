package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.place.exception.PlaceErrorCode;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import static com.yeogido.backend.domain.placeenrichment.enums.GeminiPlaceMatchStatus.VERIFIED;
import static com.yeogido.backend.domain.placeenrichment.enums.TourPlaceMatchStatus.MATCHED;

@Service
public class PlaceOverviewEnrichmentServiceImpl implements PlaceOverviewEnrichmentService {

  private final PlaceRepository placeRepository;
  private final TourPlaceEnrichmentService tourPlaceEnrichmentService;
  private final GeminiPlaceEnrichmentService geminiPlaceEnrichmentService;
  private final TransactionTemplate transactionTemplate;

  public PlaceOverviewEnrichmentServiceImpl(
      PlaceRepository placeRepository,
      TourPlaceEnrichmentService tourPlaceEnrichmentService,
      GeminiPlaceEnrichmentService geminiPlaceEnrichmentService,
      PlatformTransactionManager transactionManager
  ) {
    this.placeRepository = placeRepository;
    this.tourPlaceEnrichmentService = tourPlaceEnrichmentService;
    this.geminiPlaceEnrichmentService = geminiPlaceEnrichmentService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // 외부 API 호출 전 호출자 트랜잭션 중단 및 상태 저장 트랜잭션 분리
  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void enrich(Long placeId) {
    // 행 잠금을 통한 처리 대상 확인 및 처리 중 상태 선점
    Place place = transactionTemplate.execute(status -> {
      Place found = placeRepository.findByIdForOverviewEnrichment(placeId)
          .orElseThrow(() -> new GeneralException(PlaceErrorCode.PLACE_NOT_FOUND));
      // DB DATETIME(6) 정밀도와 시도 식별 시각 일치
      LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
      if (StringUtils.hasText(found.getOverview())) {
        return null;
      }
      // 최대 시도 횟수를 사용한 중단 건의 실패 상태 정리 및 추가 외부 조회 차단
      if (found.isOverviewProcessingStale(now)
          && found.getOverviewAttemptCount() >= Place.MAX_OVERVIEW_ATTEMPTS) {
        found.failOverviewEnrichment();
        return null;
      }
      if (!found.canStartOverviewEnrichment(now)) {
        return null;
      }
      found.startOverviewEnrichment(now);
      return found;
    });
    if (place == null) {
      return;
    }

    // 처리 중 상태 커밋 후 외부 API 조회
    try {
      Overview result = lookup(place);
      transactionTemplate.executeWithoutResult(status -> {
        Place found = findCurrentAttempt(placeId, place.getOverviewAttemptedAt());
        if (found == null) {
          return;
        }
        if (result == null) {
          found.markNoOverview();
        } else {
          found.completeOverviewEnrichment(result.text(), result.source());
        }
      });
    } catch (RuntimeException failure) {
      // 조회 또는 결과 커밋 실패 후 별도 트랜잭션으로 재시도 가능한 상태 기록
      try {
        transactionTemplate.executeWithoutResult(status -> {
          Place found = findCurrentAttempt(placeId, place.getOverviewAttemptedAt());
          if (found != null) {
            found.failOverviewEnrichment();
          }
        });
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
      }
      throw failure;
    }
  }

  private Overview lookup(Place place) {
    var tourResult = tourPlaceEnrichmentService.enrich(place);
    if (tourResult.matchStatus() == MATCHED && StringUtils.hasText(tourResult.overview())) {
      return new Overview(tourResult.overview(), PlaceOverviewSource.TOUR_API);
    }
    var input = new TourPlaceRequest.Input(place.getExternalPlaceId(), place.getName(),
        place.getRegion().getId(), place.getCategoryGroupCode(), place.getRoadAddress(),
        place.getLotAddress(), place.getLatitude(), place.getLongitude());
    // 장소 일치 검증을 통과한 Gemini 설명만 저장 대상으로 채택
    var geminiResult = geminiPlaceEnrichmentService.enrich(input);
    if (geminiResult.matchStatus() == VERIFIED && StringUtils.hasText(geminiResult.overview())) {
      return new Overview(geminiResult.overview(), PlaceOverviewSource.DAUM_GEMINI);
    }
    return null;
  }

  private Place findCurrentAttempt(Long id, LocalDateTime attemptedAt) {
    Place found = placeRepository.findByIdForOverviewEnrichment(id)
        .orElseThrow(() -> new GeneralException(PlaceErrorCode.PLACE_NOT_FOUND));
    // 장소 수정 또는 후속 시도로 대체된 이전 조회 결과의 반영 방지
    return found.getOverviewStatus() == PlaceOverviewStatus.PROCESSING
        && Objects.equals(found.getOverviewAttemptedAt(), attemptedAt) ? found : null;
  }

  private record Overview(String text, PlaceOverviewSource source) { }
}
