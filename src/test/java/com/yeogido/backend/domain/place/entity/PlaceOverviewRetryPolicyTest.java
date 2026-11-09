package com.yeogido.backend.domain.place.entity;

import com.yeogido.backend.domain.place.enums.PlaceOverviewStatus;
import com.yeogido.backend.domain.placeenrichment.enums.PlaceOverviewSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceOverviewRetryPolicyTest {
  private final LocalDateTime now = LocalDateTime.of(2026, 11, 8, 12, 0);

  @Test
  void retryWaitUsesExplicitDueTimeAndFailedNeverStartsAutomatically() {
    var place = Place.builder().build();
    place.startOverviewEnrichment(now);
    place.waitForOverviewRetry(now.plusMinutes(5), false);

    assertThat(place.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.RETRY_WAIT);
    assertThat(place.canStartOverviewEnrichment(now.plusMinutes(5).minusNanos(1))).isFalse();
    assertThat(place.canStartOverviewEnrichment(now.plusMinutes(5))).isTrue();
    place.failOverviewEnrichment();
    assertThat(place.canStartOverviewEnrichment(now.plusYears(1))).isFalse();
    assertThat(place.getOverviewNextRetryAt()).isNull();
  }

  @Test
  void actualFailuresConsumeAttemptsAndExhaustionIsTerminal() {
    var place = Place.builder().build();
    place.startOverviewEnrichment(now);
    place.waitForOverviewRetry(now.plusMinutes(5), false);
    place.startOverviewEnrichment(now.plusMinutes(5));
    place.waitForOverviewRetry(now.plusMinutes(10), false);

    assertThat(place.getOverviewAttemptCount()).isEqualTo(Place.MAX_OVERVIEW_ATTEMPTS);
    assertThat(place.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.FAILED);
    assertThat(place.getOverviewNextRetryAt()).isNull();
    assertThat(place.canStartOverviewEnrichment(now.plusDays(1))).isFalse();
  }

  @Test
  void quotaOrDeferredCallRefundsCurrentAttemptEvenAtLimit() {
    var place = Place.builder().overviewAttemptCount(1).build();
    place.startOverviewEnrichment(now);
    place.waitForOverviewRetry(now.plusDays(1), true);

    assertThat(place.getOverviewAttemptCount()).isEqualTo(1);
    assertThat(place.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.RETRY_WAIT);
    assertThat(place.canStartOverviewEnrichment(now.plusDays(1))).isTrue();
  }

  @Test
  void refundCannotMakeAttemptCountNegative() {
    var place = Place.builder().build();
    place.waitForOverviewRetry(now, true);
    assertThat(place.getOverviewAttemptCount()).isZero();
  }

  @Test
  void deferringBeforeClaimDoesNotConsumeAnAttempt() {
    var place = Place.builder().build();
    place.waitForOverviewRetry(now.plusDays(1), false);

    assertThat(place.getOverviewAttemptCount()).isZero();
    assertThat(place.getOverviewAttemptedAt()).isNull();
    assertThat(place.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.RETRY_WAIT);
  }

  @Test
  void startingOrFinishingAnAttemptClearsNextRetryTime() {
    var place = waitingPlace();
    place.startOverviewEnrichment(now);
    assertThat(place.getOverviewNextRetryAt()).isNull();
    place = waitingPlace();
    place.completeOverviewEnrichment("설명", PlaceOverviewSource.TOUR_API);
    assertThat(place.getOverviewNextRetryAt()).isNull();
    place = waitingPlace();
    place.markNoOverview();
    assertThat(place.getOverviewNextRetryAt()).isNull();
  }

  @Test
  void editingPlaceResetsAttemptsAndNextRetryTime() {
    var place = waitingPlace();
    place.updateTourData(null, "변경된 장소", "주소", null, BigDecimal.ONE, BigDecimal.TEN);
    assertThat(place.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.PENDING);
    assertThat(place.getOverviewAttemptCount()).isZero();
    assertThat(place.getOverviewAttemptedAt()).isNull();
    assertThat(place.getOverviewNextRetryAt()).isNull();
  }

  @Test
  void unchangedPlaceDataPreservesRetryState() {
    var place = waitingPlace();
    place.updateTourData(null, null, null, null, null, null);
    assertThat(place.getOverviewStatus()).isEqualTo(PlaceOverviewStatus.RETRY_WAIT);
    assertThat(place.getOverviewNextRetryAt()).isEqualTo(now);
    assertThat(place.getOverviewAttemptCount()).isEqualTo(1);
  }

  @Test
  void staleProcessingRecoveryBoundaryAndLimitAreInclusive() {
    var place = Place.builder().overviewStatus(PlaceOverviewStatus.PROCESSING)
        .overviewAttemptCount(1)
        .overviewAttemptedAt(now.minusMinutes(Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES)).build();
    assertThat(place.canStartOverviewEnrichment(now.minusNanos(1))).isFalse();
    assertThat(place.canStartOverviewEnrichment(now)).isTrue();
    var exhausted = Place.builder().overviewStatus(PlaceOverviewStatus.PROCESSING)
        .overviewAttemptCount(Place.MAX_OVERVIEW_ATTEMPTS)
        .overviewAttemptedAt(now.minusMinutes(Place.OVERVIEW_PROCESSING_TIMEOUT_MINUTES)).build();
    assertThat(exhausted.isOverviewProcessingStale(now)).isTrue();
    assertThat(exhausted.canStartOverviewEnrichment(now)).isFalse();
  }

  @Test
  void retryWaitWithoutDueTimeDoesNotStart() {
    var place = Place.builder().overviewStatus(PlaceOverviewStatus.RETRY_WAIT).build();
    assertThat(place.canStartOverviewEnrichment(now)).isFalse();
  }

  private Place waitingPlace() {
    var place = Place.builder().build();
    place.startOverviewEnrichment(now.minusMinutes(1));
    place.waitForOverviewRetry(now, false);
    return place;
  }
}
