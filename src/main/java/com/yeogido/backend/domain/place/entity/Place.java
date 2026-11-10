package com.yeogido.backend.domain.place.entity;

import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.util.StringUtils;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
    name = "place",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_place_source_external_id",
            columnNames = {"source", "external_place_id"}
        )
    }
)
public class Place extends BaseEntity {

  public static final int MAX_OVERVIEW_ATTEMPTS = 2;
  public static final int OVERVIEW_RETRY_DELAY_MINUTES = 5;
  // 지역 코드 페이지 조회와 장소 검색, Daum/Gemini 순차 호출을 고려한 복구 대기 시간
  public static final int OVERVIEW_PROCESSING_TIMEOUT_MINUTES = 60;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "region_id", nullable = false)
  private Region region;

  @Column(nullable = false, name = "external_place_id", length = 100)
  private String externalPlaceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PlaceSource source;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(name = "category_group_code", length = 20)
  private String categoryGroupCode;

  @Column(name = "road_address", length = 255)
  private String roadAddress;

  @Column(name = "lot_address", length = 255)
  private String lotAddress;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal latitude;

  @Column(nullable = false, precision = 10, scale = 7)
  private BigDecimal longitude;

  @Column(columnDefinition = "TEXT")
  private String overview;

  @Enumerated(EnumType.STRING)
  @Column(name = "overview_source", length = 20)
  private PlaceOverviewSource overviewSource;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "overview_status", nullable = false, length = 20)
  private PlaceOverviewStatus overviewStatus = PlaceOverviewStatus.PENDING;

  @Column(name = "overview_attempted_at")
  private LocalDateTime overviewAttemptedAt;

  @Builder.Default
  @Column(name = "overview_attempt_count", nullable = false)
  private int overviewAttemptCount = 0;

  @Column(name = "overview_next_retry_at")
  private LocalDateTime overviewNextRetryAt;

  @Column(name = "overview_blocked_by_operation", length = 100)
  private String overviewBlockedByOperation;

  // 최종 실패의 자동 재시도 제외 및 예약 시각, 중단 복구 기준에 따른 시작 가능 여부 판단
  public boolean canStartOverviewEnrichment(LocalDateTime now) {
    if (overviewAttemptCount >= MAX_OVERVIEW_ATTEMPTS) {
      return false;
    }
    return isOverviewProcessingStale(now)
        || overviewStatus == PlaceOverviewStatus.PENDING
        || overviewStatus == PlaceOverviewStatus.RETRY_WAIT
        && overviewNextRetryAt != null
        && !overviewNextRetryAt.isAfter(now);
  }

  public boolean isOverviewProcessingStale(LocalDateTime now) {
    return overviewStatus == PlaceOverviewStatus.PROCESSING
        && overviewAttemptedAt != null
        && !overviewAttemptedAt.isAfter(now.minusMinutes(OVERVIEW_PROCESSING_TIMEOUT_MINUTES));
  }

  public void startOverviewEnrichment(LocalDateTime attemptedAt) {
    this.overviewStatus = PlaceOverviewStatus.PROCESSING;
    this.overviewAttemptedAt = attemptedAt;
    this.overviewAttemptCount++;
    this.overviewNextRetryAt = null;
    this.overviewBlockedByOperation = null;
  }

  public void completeOverviewEnrichment(String overview, PlaceOverviewSource source) {
    this.overview = overview;
    this.overviewSource = source;
    this.overviewStatus = PlaceOverviewStatus.COMPLETED;
    this.overviewNextRetryAt = null;
    this.overviewBlockedByOperation = null;
  }

  public void markNoOverview() {
    this.overview = null;
    this.overviewSource = null;
    this.overviewStatus = PlaceOverviewStatus.NO_OVERVIEW;
    this.overviewNextRetryAt = null;
    this.overviewBlockedByOperation = null;
  }

  public void failOverviewEnrichment() {
    this.overviewStatus = PlaceOverviewStatus.FAILED;
    this.overviewNextRetryAt = null;
    this.overviewBlockedByOperation = null;
  }

  public void waitForOverviewRetry(LocalDateTime retryAt, boolean refundAttempt) {
    setOverviewRetry(Objects.requireNonNull(retryAt, "retryAt"), refundAttempt);
  }

  private void setOverviewRetry(LocalDateTime retryAt, boolean refundAttempt) {
    // 공통 차단, 호출 보류에 따른 현재 시도 환급 및 음수 방지
    if (refundAttempt) {
      this.overviewAttemptCount = Math.max(0, this.overviewAttemptCount - 1);
    }
    // 실제 실패 횟수 소진 시 대기 예약 대신 최종 실패 확정
    if (this.overviewAttemptCount >= MAX_OVERVIEW_ATTEMPTS) {
      failOverviewEnrichment();
      return;
    }
    this.overviewStatus = PlaceOverviewStatus.RETRY_WAIT;
    this.overviewNextRetryAt = retryAt;
    this.overviewBlockedByOperation = null;
  }

  public void waitForOverviewApi(LocalDateTime retryAt, String operationKey) {
    setOverviewRetry(retryAt, true);
    if (overviewStatus == PlaceOverviewStatus.RETRY_WAIT && retryAt == null) {
      this.overviewBlockedByOperation = Objects.requireNonNull(operationKey, "operationKey");
    }
  }

  public void updateTourData(
      Region region,
      String name,
      String roadAddress,
      String lotAddress,
      BigDecimal latitude,
      BigDecimal longitude
  ) {
    if (!Objects.equals(this.name, name)
        || !Objects.equals(this.roadAddress, roadAddress)
        || !Objects.equals(this.lotAddress, lotAddress)
        || !sameCoordinate(this.latitude, latitude)
        || !sameCoordinate(this.longitude, longitude)) {
      this.overview = null;
      this.overviewSource = null;
      this.overviewStatus = PlaceOverviewStatus.PENDING;
      this.overviewAttemptedAt = null;
      this.overviewAttemptCount = 0;
      this.overviewNextRetryAt = null;
      this.overviewBlockedByOperation = null;
    }
    this.region = region;
    this.name = name;
    this.roadAddress = roadAddress;
    this.lotAddress = lotAddress;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  private static boolean sameCoordinate(BigDecimal current, BigDecimal updated) {
    return current == null ? updated == null : updated != null && current.compareTo(updated) == 0;
  }

}
