package com.yeogido.backend.domain.placeenrichment.scheduler;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewEnrichmentService;
import com.yeogido.backend.global.logging.ScheduledExecution;
import com.yeogido.backend.global.logging.SafeLogDetails;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Profile("!test")
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.place-overview.retry", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class PlaceOverviewRetryScheduler {

  private final PlaceRepository placeRepository;
  private final PlaceOverviewEnrichmentService enrichmentService;

  @Scheduled(fixedDelayString = "${app.place-overview.retry.fixed-delay-ms:60000}",
      initialDelayString = "${app.place-overview.retry.fixed-delay-ms:60000}")
  public void retryFailedOverviews() {
    try (var execution = ScheduledExecution.start("placeOverviewRetry")) {
      try {
        var now = LocalDateTime.now();
        var placeIds = placeRepository.findOverviewRetryCandidates(
            now.minusMinutes(Place.OVERVIEW_RETRY_DELAY_MINUTES),
            now.minusMinutes(Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES),
            Place.MAX_OVERVIEW_ATTEMPTS, PageRequest.of(0, 20));
        int successCount = 0;
        int failedCount = 0;
        for (Long placeId : placeIds) {
          try {
            // 서비스 내부 행 잠금을 통한 재시도 시각 및 횟수 재확인
            enrichmentService.enrich(placeId);
            successCount++;
          } catch (RuntimeException exception) {
            failedCount++;
            // 외부 API 인증 정보 및 응답 노출 방지를 위한 예외 원문 제외
            log.atWarn().addKeyValue("event", "place.overview.processing.failed")
                .addKeyValue("operation", "retryFailedOverview").addKeyValue("placeId", placeId)
                .addKeyValue("failureType", SafeLogDetails.failureType(exception))
                .addKeyValue("exceptionType", exception.getClass().getName())
                .log("Place overview processing failed.");
          }
        }
        // 설명 생성 건수가 아닌 서비스 호출 완료 및 실패 건수 집계
        execution.counts(successCount + failedCount, successCount, failedCount);
        execution.complete();
      } catch (RuntimeException | Error failure) {
        execution.failed(failure);
        throw failure;
      }
    }
  }
}
