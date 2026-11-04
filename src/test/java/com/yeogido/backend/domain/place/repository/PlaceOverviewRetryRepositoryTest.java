package com.yeogido.backend.domain.place.repository;

import com.yeogido.backend.domain.place.entity.Place;
import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.place.enums.PlaceSource;
import com.yeogido.backend.domain.region.entity.Region;
import com.yeogido.backend.domain.region.enums.RegionType;
import com.yeogido.backend.domain.region.repository.RegionRepository;
import com.yeogido.backend.global.config.JpaConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PlaceOverviewRetryRepositoryTest {
  @Autowired PlaceRepository places;
  @Autowired RegionRepository regions;

  @Test
  void selectsOnlyPendingPlacesWithoutExistingOverviewOrExhaustedAttempts() {
    var region = regions.save(Region.builder().name("서울").type(RegionType.REGION).build());
    var pending = save(region, "pending", PlaceOverviewStatus.PENDING, 0, null, null);
    var blank = save(region, "blank", PlaceOverviewStatus.PENDING, 0, null, " ");
    save(region, "existing", PlaceOverviewStatus.PENDING, 0, null, "기존 설명");
    save(region, "exhausted", PlaceOverviewStatus.PENDING, 2, null, null);
    for (var status : PlaceOverviewStatus.values()) {
      if (status != PlaceOverviewStatus.PENDING) {
        save(region, status.name(), status, 1, LocalDateTime.now().minusMinutes(6), null);
      }
    }

    assertThat(places.findOverviewPendingCandidates(Place.MAX_OVERVIEW_ATTEMPTS, PageRequest.of(0, 20)))
        .containsExactly(pending.getId(), blank.getId());
  }

  @Test
  void selectsOnlyDueFailedPlacesBelowAttemptLimit() {
    var retryBefore = LocalDateTime.of(2026, 11, 2, 12, 0)
        .minusMinutes(Place.OVERVIEW_RETRY_DELAY_MINUTES);
    var region = regions.save(Region.builder().name("서울").type(RegionType.REGION).build());
    var first = save(region, "first", PlaceOverviewStatus.FAILED, 1, retryBefore.minusMinutes(1), null);
    var second = save(region, "second", PlaceOverviewStatus.FAILED, 1, retryBefore, null);
    save(region, "future", PlaceOverviewStatus.FAILED, 1, retryBefore.plusSeconds(1), null);
    save(region, "exhausted", PlaceOverviewStatus.FAILED, 2, retryBefore.minusMinutes(2), null);
    save(region, "unscheduled", PlaceOverviewStatus.FAILED, 1, null, null);
    save(region, "existing", PlaceOverviewStatus.FAILED, 1, retryBefore, "기존 설명");
    for (var status : PlaceOverviewStatus.values()) {
      if (status != PlaceOverviewStatus.FAILED) {
        save(region, status.name(), status, 1, retryBefore.minusMinutes(2), null);
      }
    }
    assertThat(places.findOverviewRetryCandidates(retryBefore, retryBefore.minusMinutes(60), Place.MAX_OVERVIEW_ATTEMPTS, PageRequest.of(0, 20)))
        .containsExactly(first.getId(), second.getId());
  }

  @Test
  void selectsStaleProcessingIncludingExhaustedAttemptsButExcludesActiveOrDescribedPlaces() {
    var now = LocalDateTime.of(2026, 11, 2, 12, 0);
    var staleBefore = now.minusMinutes(Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES);
    var region = regions.save(Region.builder().name("서울").type(RegionType.REGION).build());
    var exhausted = save(region, "exhausted-processing", PlaceOverviewStatus.PROCESSING,
        Place.MAX_OVERVIEW_ATTEMPTS, staleBefore.minusSeconds(1), null);
    var due = save(region, "due-processing", PlaceOverviewStatus.PROCESSING, 1, staleBefore, null);
    save(region, "active-processing", PlaceOverviewStatus.PROCESSING, 1, staleBefore.plusSeconds(1), null);
    save(region, "missing-time", PlaceOverviewStatus.PROCESSING, 1, null, null);
    save(region, "has-overview", PlaceOverviewStatus.PROCESSING, 1, staleBefore, "기존 설명");
    save(region, "completed", PlaceOverviewStatus.COMPLETED, 1, staleBefore, null);

    assertThat(places.findOverviewRetryCandidates(now.minusMinutes(5), staleBefore,
        Place.MAX_OVERVIEW_ATTEMPTS, PageRequest.of(0, 20)))
        .containsExactly(exhausted.getId(), due.getId());
  }

  private Place save(Region region, String id, PlaceOverviewStatus status, int count,
      LocalDateTime attemptedAt, String overview) {
    return places.save(Place.builder().region(region).externalPlaceId(id).source(PlaceSource.KAKAO)
        .name(id).latitude(BigDecimal.valueOf(37)).longitude(BigDecimal.valueOf(127))
        .overviewStatus(status).overviewAttemptCount(count).overviewAttemptedAt(attemptedAt).overview(overview)
        .build());
  }
}
