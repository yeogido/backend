package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.place.exception.PlaceErrorCode;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.dto.request.TourPlaceRequest;
import com.yeogido.backend.domain.placeenrichment.dto.response.TourPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.entity.PlaceOverviewApiOperation;
import com.yeogido.backend.domain.placeenrichment.dto.response.GeminiPlaceResponse;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;
import com.yeogido.backend.domain.placeenrichment.client.PlaceOverviewApiScope;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiDeferredException;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PlaceOverviewEnrichmentServiceImpl implements PlaceOverviewEnrichmentService {

  private final PlaceRepository placeRepository;
  private final TourPlaceEnrichmentService tourPlaceEnrichmentService;
  private final GeminiPlaceEnrichmentService geminiPlaceEnrichmentService;
  private final TransactionTemplate transactionTemplate;
  private final PlaceOverviewApiGate apiGate;

  public PlaceOverviewEnrichmentServiceImpl(
      PlaceRepository placeRepository,
      TourPlaceEnrichmentService tourPlaceEnrichmentService,
      GeminiPlaceEnrichmentService geminiPlaceEnrichmentService,
      PlatformTransactionManager transactionManager,
      PlaceOverviewApiGate apiGate
  ) {
    this.placeRepository = placeRepository;
    this.tourPlaceEnrichmentService = tourPlaceEnrichmentService;
    this.geminiPlaceEnrichmentService = geminiPlaceEnrichmentService;
    this.apiGate = apiGate;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // 외부 API 호출 전 호출자 트랜잭션 중단 및 상태 저장 트랜잭션 분리
  @Override
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void enrich(Long placeId) {
    Place snapshot = placeRepository.findById(placeId)
        .orElseThrow(() -> new GeneralException(PlaceErrorCode.PLACE_NOT_FOUND));
    if (StringUtils.hasText(snapshot.getOverview())) {
      return;
    }
    if (snapshot.canStartOverviewEnrichment(LocalDateTime.now())) {
      // 불필요한 선점과 시도 횟수 증가 방지를 위한 필수 검색의 공통 차단 사전 확인
      apiGate.check("tour", "/searchKeyword2");
    }
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
    String stage = "lookup";
    try (var ignored = PlaceOverviewApiScope.open(apiGate)) {
      Overview result = lookup(place);
      stage = "persist";
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
      String failureStage = stage;
      // 조회 또는 결과 커밋 실패 후 별도 트랜잭션으로 재시도 가능한 상태 기록
      try {
        Place decision = transactionTemplate.execute(status -> {
          // API 작업 행 → 장소 행 순서로 잠가 차단 해제와 대기 저장의 경합 방지
          LocalDateTime sharedRetryAt = sharedRetryAt(failure);
          Place found = findCurrentAttempt(placeId, place.getOverviewAttemptedAt());
          if (found != null) {
            applyFailure(found, failure, sharedRetryAt);
          }
          return found;
        });
        // 상태 저장 커밋 이후 확정된 재시도 결정만 로그 기록
        if (decision != null) {
          logDecision(decision, failure, failureStage);
        }
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
      }
      throw failure;
    }
  }

  private Overview lookup(Place place) {
    TourPlaceResponse.MatchResult tourResult;
    try {
      tourResult = tourPlaceEnrichmentService.enrich(place);
    } catch (GeneralException failure) {
      throw classifyServiceFailure(failure, "tour", "/searchKeyword2");
    }
    if (tourResult.matchStatus() == MATCHED && StringUtils.hasText(tourResult.overview())) {
      return new Overview(tourResult.overview(), PlaceOverviewSource.TOUR_API);
    }
    var input = new TourPlaceRequest.Input(place.getExternalPlaceId(), place.getName(),
        place.getRegion().getId(), place.getCategoryGroupCode(), place.getRoadAddress(),
        place.getLotAddress(), place.getLatitude(), place.getLongitude());
    // 장소 일치 검증을 통과한 Gemini 설명만 저장 대상으로 채택
    GeminiPlaceResponse.Result geminiResult;
    try {
      geminiResult = geminiPlaceEnrichmentService.enrich(input);
    } catch (GeneralException failure) {
      throw classifyServiceFailure(failure, "gemini", "generateContent");
    }
    if (geminiResult.matchStatus() == VERIFIED && StringUtils.hasText(geminiResult.overview())) {
      return new Overview(geminiResult.overview(), PlaceOverviewSource.DAUM_GEMINI);
    }
    return null;
  }

  private GeneralException classifyServiceFailure(GeneralException failure, String provider,
      String operation) {
    // 클라이언트에서 분류한 실제 공급자, API 작업 정보 유지
    if (failure instanceof ExternalApiFailure) {
      return failure;
    }
    var code = failure.getErrorCode();
    ExternalApiFailureKind kind;
    if (code == PlaceEnrichmentErrorCode.INVALID_TOUR_PLACE_SEARCH_CONFIG
        || code == PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG) {
      kind = ExternalApiFailureKind.CONFIGURATION;
    } else if (code == PlaceEnrichmentErrorCode.PLACE_NAME_REQUIRED
        || code == PlaceEnrichmentErrorCode.GEMINI_ENRICHMENT_INPUT_REQUIRED) {
      kind = ExternalApiFailureKind.REQUEST;
    } else {
      kind = ExternalApiFailureKind.INVALID_RESPONSE;
    }
    var classified = new ExternalApiFailure(code, provider, operation, kind, null, null, null);
    apiGate.recordFailure(classified);
    return classified;
  }

  private void applyFailure(Place place, RuntimeException failure, LocalDateTime sharedRetryAt) {
    LocalDateTime now = LocalDateTime.now();
    // 실제 외부 요청 없이 보류된 현재 시도의 횟수 환급 및 대기 예약
    if (failure instanceof ExternalApiDeferredException deferred) {
      place.waitForOverviewApi(sharedRetryAt, PlaceOverviewApiOperation.key(deferred.getProvider(), deferred.getOperation()));
      return;
    }
    if (failure instanceof ExternalApiFailure external) {
      switch (external.getKind()) {
        case REQUEST -> {
          // 동일 입력 재시도로 해결되지 않는 요청 오류의 즉시 최종 실패 처리
          place.failOverviewEnrichment();
          return;
        }
        case DAILY_QUOTA, RATE_LIMIT, AUTHENTICATION, CONFIGURATION -> {
          // 장소별 실패 횟수 소진 방지를 위한 공통 제한 시도의 횟수 환급
          place.waitForOverviewApi(sharedRetryAt, PlaceOverviewApiOperation.key(external.getProvider(), external.getOperation()));
          return;
        }
        default -> { }
      }
    }
    // 실제 조회, 검증, 저장 실패의 시도 횟수 사용 및 실패 완료 시점 기준 대기
    long backoffSeconds = Duration.ofMinutes(Place.OVERVIEW_RETRY_DELAY_MINUTES).toSeconds();
    LocalDateTime retryAt = now.plusSeconds(backoffSeconds + ThreadLocalRandom.current().nextLong(31));
    // 기본 대기 시간보다 긴 공급자 지정 Retry-After 우선 적용
    if (failure instanceof ExternalApiFailure external && external.getRetryAfter() != null) {
      LocalDateTime upstreamRetryAt = ExternalApiFailure.retryAt(now,
          ExternalApiFailure.capRetryAfter(external.getRetryAfter()));
      if (upstreamRetryAt.isAfter(retryAt)) {
        retryAt = upstreamRetryAt;
      }
    }
    place.waitForOverviewRetry(retryAt, false);
  }

  private LocalDateTime sharedRetryAt(RuntimeException failure) {
    if (failure instanceof ExternalApiDeferredException deferred) {
      LocalDateTime retryAt = apiGate.retryAtForUpdate(deferred.getProvider(), deferred.getOperation());
      // 수동 차단 해제 여부 재확인 및 공급자 지정 유한 대기 시간 보존
      if (deferred.getRetryAt() != null
          && retryAt != null && deferred.getRetryAt().isAfter(retryAt)) {
        return deferred.getRetryAt();
      }
      return retryAt;
    }
    if (failure instanceof ExternalApiFailure external) {
      return switch (external.getKind()) {
        case DAILY_QUOTA, RATE_LIMIT -> {
          LocalDateTime retryAt = apiGate.retryAtForUpdate(external.getProvider(), external.getOperation());
          if (retryAt != null && external.getRetryAfter() != null) {
            LocalDateTime providerRetryAt = ExternalApiFailure.retryAt(LocalDateTime.now(),
                ExternalApiFailure.capRetryAfter(external.getRetryAfter()));
            if (providerRetryAt.isAfter(retryAt)) {
              retryAt = providerRetryAt;
            }
          }
          yield retryAt;
        }
        case AUTHENTICATION, CONFIGURATION ->
            apiGate.retryAtForUpdate(external.getProvider(), external.getOperation());
        default -> null;
      };
    }
    return null;
  }

  private void logDecision(Place place, RuntimeException failure, String stage) {
    var event = log.atWarn().addKeyValue("event", "place.overview.retry.decided")
        .addKeyValue("placeId", place.getId())
        .addKeyValue("stage", stage)
        .addKeyValue("attemptCount", place.getOverviewAttemptCount())
        .addKeyValue("retryDecision", place.getOverviewStatus() == PlaceOverviewStatus.FAILED
            ? "stop" : "wait")
        .addKeyValue("nextRetryAt", place.getOverviewNextRetryAt());
    if (failure instanceof ExternalApiFailure external) {
      event.addKeyValue("provider", external.getProvider())
          .addKeyValue("operation", external.getOperation())
          .addKeyValue("failureType", external.failureType())
          .addKeyValue("errorCode", external.getErrorCode().getCode())
          .addKeyValue("providerCode", external.getProviderCode())
          .addKeyValue("upstreamHttpStatus", external.getUpstreamHttpStatus());
    } else if (failure instanceof ExternalApiDeferredException deferred) {
      event.addKeyValue("provider", deferred.getProvider())
          .addKeyValue("operation", deferred.getOperation())
          .addKeyValue("failureType", "shared_dependency_blocked");
    } else {
      event.addKeyValue("failureType", SafeLogDetails.failureType(failure))
          .addKeyValue("exceptionType", failure.getClass().getName());
    }
    event.log("Place overview retry decision recorded.");
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
