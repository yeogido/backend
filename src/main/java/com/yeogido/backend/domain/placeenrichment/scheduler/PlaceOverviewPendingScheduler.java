package com.yeogido.backend.domain.placeenrichment.scheduler;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.repository.PlaceRepository;
import com.yeogido.backend.domain.placeenrichment.service.PlaceOverviewEnrichmentService;
import com.yeogido.backend.global.logging.ScheduledExecution;
import com.yeogido.backend.global.logging.SafeLogDetails;
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
@ConditionalOnProperty(prefix = "app.place-overview.pending", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class PlaceOverviewPendingScheduler {

  private final PlaceRepository placeRepository;
  private final PlaceOverviewEnrichmentService enrichmentService;

  @Scheduled(fixedDelayString = "${app.place-overview.pending.fixed-delay-ms:60000}",
      initialDelayString = "${app.place-overview.pending.fixed-delay-ms:60000}")
  public void enrichPendingOverviews() {
    try (var execution = ScheduledExecution.start("placeOverviewPending")) {
      try {
        var placeIds = placeRepository.findOverviewPendingCandidates(
            Place.MAX_OVERVIEW_ATTEMPTS, PageRequest.of(0, 20));
        int successCount = 0;
        int failedCount = 0;
        for (Long placeId : placeIds) {
          try {
            // 서비스 내부 행 잠금을 통한 중복 처리 방지
            enrichmentService.enrich(placeId);
            successCount++;
          } catch (RuntimeException exception) {
            failedCount++;
            // 실패 상태 기록 이후 기존 FAILED 스케줄러를 통한 재시도
            log.atWarn().addKeyValue("event", "place.overview.processing.failed")
                .addKeyValue("operation", "enrichPendingOverview").addKeyValue("placeId", placeId)
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
