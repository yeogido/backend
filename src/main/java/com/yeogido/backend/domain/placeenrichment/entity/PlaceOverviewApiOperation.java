package com.yeogido.backend.domain.placeenrichment.entity;

import com.yeogido.backend.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "place_overview_api_operation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceOverviewApiOperation extends BaseEntity {
  @Id
  @Column(length = 100)
  private String id;

  @Column(name = "blocked_until")
  private LocalDateTime blockedUntil;

  @Column(name = "manual_release_required", nullable = false)
  private boolean manualReleaseRequired;

  public PlaceOverviewApiOperation(String provider, String operation) {
    this.id = key(provider, operation);
  }

  public static String key(String provider, String operation) {
    return provider + ":" + operation;
  }

  public boolean isBlocked(LocalDateTime now) {
    return manualReleaseRequired || blockedUntil != null && blockedUntil.isAfter(now);
  }

  // 동시 실패로 기존 차단 시간이 단축되는 상황 방지를 위한 최대 시각 유지
  public void blockUntil(LocalDateTime until) {
    if (blockedUntil == null || until.isAfter(blockedUntil)) {
      blockedUntil = until;
    }
  }

  public void blockManually() {
    manualReleaseRequired = true;
  }

  public void release() {
    manualReleaseRequired = false;
    blockedUntil = null;
  }
}
