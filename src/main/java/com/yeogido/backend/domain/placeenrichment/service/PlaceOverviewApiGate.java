package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.place.repository.PlaceRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.yeogido.backend.domain.placeenrichment.entity.PlaceOverviewApiOperation;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiDeferredException;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.repository.PlaceOverviewApiOperationRepository;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class PlaceOverviewApiGate {
  public static final Duration RELEASE_RETRY_DELAY = Duration.ofMinutes(5);
  public static final Duration RECHECK_DELAY = Duration.ofMinutes(1);

  private final PlaceOverviewApiOperationRepository operations;
  private final TransactionTemplate transaction;
  private final Clock clock;
  private final PlaceRepository places;
  private final long dailyQuotaCooldownSeconds;
  private final long rateLimitCooldownSeconds;
  private final long transientCooldownSeconds;

  @Autowired
  public PlaceOverviewApiGate(PlaceOverviewApiOperationRepository operations, PlaceRepository places,
      PlatformTransactionManager transactionManager,
      @Value("${app.place-overview.external-api.daily-quota-cooldown-seconds:86400}") long dailyQuotaCooldownSeconds,
      @Value("${app.place-overview.external-api.rate-limit-cooldown-seconds:60}") long rateLimitCooldownSeconds,
      @Value("${app.place-overview.external-api.transient-cooldown-seconds:30}") long transientCooldownSeconds) {
    this(operations, places, transactionManager, dailyQuotaCooldownSeconds, rateLimitCooldownSeconds,
        transientCooldownSeconds, Clock.systemDefaultZone());
  }

  PlaceOverviewApiGate(PlaceOverviewApiOperationRepository operations, PlaceRepository places,
      PlatformTransactionManager transactionManager, long dailyQuotaCooldownSeconds,
      long rateLimitCooldownSeconds, long transientCooldownSeconds, Clock clock) {
    if (dailyQuotaCooldownSeconds < 1 || dailyQuotaCooldownSeconds > ExternalApiFailure.MAX_COOLDOWN.toSeconds()
        || rateLimitCooldownSeconds < 1 || rateLimitCooldownSeconds > Duration.ofDays(1).toSeconds()
        || transientCooldownSeconds < 1 || transientCooldownSeconds > Duration.ofDays(1).toSeconds()) {
      throw new GeneralException(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG);
    }
    this.operations = operations;
    this.places = places;
    transaction = new TransactionTemplate(transactionManager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
    this.dailyQuotaCooldownSeconds = dailyQuotaCooldownSeconds;
    this.rateLimitCooldownSeconds = rateLimitCooldownSeconds;
    this.transientCooldownSeconds = transientCooldownSeconds;
  }

  public void check(String provider, String operation) {
    PlaceOverviewApiOperation state = operations.findById(PlaceOverviewApiOperation.key(provider, operation))
        .orElseThrow(() -> missingOperation(provider, operation));
    requireAvailable(state, provider, operation, now());
  }

  public void recordFailure(ExternalApiFailure failure) {
    // 요청, 응답 검증 오류를 제외한 공급자 공통 장애의 종류별 차단 시간 결정
    Duration delay = switch (failure.getKind()) {
      case DAILY_QUOTA -> {
        Duration configured = Duration.ofSeconds(dailyQuotaCooldownSeconds);
        Duration requested = retryAfterOr(failure, dailyQuotaCooldownSeconds);
        // 일일 한도 기본 대기 시간과 공급자 지정 시간 중 더 긴 값 적용
        yield requested.compareTo(configured) > 0 ? requested : configured;
      }
      case RATE_LIMIT -> retryAfterOr(failure, rateLimitCooldownSeconds);
      case TIMEOUT, CONNECTION, SERVER -> retryAfterOr(failure, transientCooldownSeconds);
      case AUTHENTICATION, CONFIGURATION -> null;
      case REQUEST, INVALID_RESPONSE -> Duration.ZERO;
    };
    if (Duration.ZERO.equals(delay)) {
      return;
    }
    LocalDateTime until = delay == null ? null : ExternalApiFailure.retryAt(now(), delay);
    // 장소 상태 저장과 분리된 트랜잭션 및 행 잠금을 통한 서버 간 공통 차단 갱신
    LocalDateTime blockedUntil = transaction.execute(status -> {
      PlaceOverviewApiOperation state = findForUpdate(failure.getProvider(), failure.getOperation());
      if (delay == null) {
        state.blockManually();
      } else {
        state.blockUntil(until);
      }
      return state.isManualReleaseRequired() ? null : state.getBlockedUntil();
    });
    log.atWarn().addKeyValue("event", "place.overview.external_api.blocked")
        .addKeyValue("provider", failure.getProvider()).addKeyValue("operation", failure.getOperation())
        .addKeyValue("failureType", failure.failureType()).addKeyValue("retryAt", blockedUntil)
        .addKeyValue("manualReleaseRequired", delay == null)
        .log("Overview external API operation blocked.");
  }

  // 설정 수정 또는 한도 회복 확인 후 해당 API 작업의 공통 차단 해제
  public void release(String provider, String operation) {
    transaction.executeWithoutResult(status -> {
      findForUpdate(provider, operation).release();
      places.resumeManualOverviewWaits(PlaceOverviewApiOperation.key(provider, operation),
          now().plus(RELEASE_RETRY_DELAY));
    });
    log.atInfo().addKeyValue("event", "place.overview.external_api.released")
        .addKeyValue("provider", provider).addKeyValue("operation", operation)
        .log("Overview external API operation released.");
  }

  // 장소 저장과 차단 해제의 작업 행 잠금 순서 일치를 위한 호출자 트랜잭션 참여
  @Transactional(propagation = Propagation.MANDATORY)
  public LocalDateTime retryAtForUpdate(String provider, String operation) {
    var state = operations.findForUpdate(PlaceOverviewApiOperation.key(provider, operation));
    LocalDateTime now = now();
    if (state.isPresent() && state.get().isBlocked(now)) {
      return state.get().isManualReleaseRequired() ? null : state.get().getBlockedUntil();
    }
    // 해제가 먼저 끝난 경우에도 동일하게 5분 뒤 재시도
    return now.plus(RELEASE_RETRY_DELAY);
  }

  private Duration retryAfterOr(ExternalApiFailure failure, long fallbackSeconds) {
    Duration retryAfter = failure.getRetryAfter();
    if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
      return Duration.ofSeconds(fallbackSeconds);
    }
    return ExternalApiFailure.capRetryAfter(retryAfter);
  }

  private void requireAvailable(PlaceOverviewApiOperation state, String provider, String operation,
      LocalDateTime now) {
    if (state.isBlocked(now)) {
      throw new ExternalApiDeferredException(provider, operation,
          state.isManualReleaseRequired() ? null : state.getBlockedUntil());
    }
  }

  private PlaceOverviewApiOperation findForUpdate(String provider, String operation) {
    return operations.findForUpdate(PlaceOverviewApiOperation.key(provider, operation))
        .orElseThrow(() -> missingOperation(provider, operation));
  }

  // 차단 기준 행 누락 시 무제한 호출 방지를 위한 오류 기록 및 처리 보류
  private ExternalApiDeferredException missingOperation(String provider, String operation) {
    LocalDateTime retryAt = now().plus(RECHECK_DELAY);
    log.atError().addKeyValue("event", "place.overview.external_api.configuration_missing")
        .addKeyValue("provider", provider).addKeyValue("operation", operation)
        .addKeyValue("failureType", "CONFIGURATION").addKeyValue("retryAt", retryAt)
        .log("Overview external API operation configuration missing.");
    return new ExternalApiDeferredException(provider, operation, retryAt);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }
}
