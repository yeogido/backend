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

  public boolean canStartOverviewEnrichment(LocalDateTime now) {
    if (overviewAttemptCount >= MAX_OVERVIEW_ATTEMPTS) {
      return false;
    }
    return overviewStatus == PlaceOverviewStatus.PENDING
        || overviewStatus == PlaceOverviewStatus.FAILED
        && overviewAttemptedAt != null
        && !overviewAttemptedAt.isAfter(now.minusMinutes(OVERVIEW_RETRY_DELAY_MINUTES));
  }

  public void startOverviewEnrichment(LocalDateTime attemptedAt) {
    this.overviewStatus = PlaceOverviewStatus.PROCESSING;
    this.overviewAttemptedAt = attemptedAt;
    this.overviewAttemptCount++;
  }

  public void completeOverviewEnrichment(String overview, PlaceOverviewSource source) {
    this.overview = overview;
    this.overviewSource = source;
    this.overviewStatus = PlaceOverviewStatus.COMPLETED;
  }

  public void markNoOverview() {
    this.overview = null;
    this.overviewSource = null;
    this.overviewStatus = PlaceOverviewStatus.NO_OVERVIEW;
  }

  public void failOverviewEnrichment() {
    this.overviewStatus = PlaceOverviewStatus.FAILED;
  }

  public void updateTourData(
      Region region,
      String name,
      String roadAddress,
      String lotAddress,
      BigDecimal latitude,
      BigDecimal longitude
  ) {
    this.region = region;
    this.name = name;
    this.roadAddress = roadAddress;
    this.lotAddress = lotAddress;
    this.latitude = latitude;
    this.longitude = longitude;
  }

}
