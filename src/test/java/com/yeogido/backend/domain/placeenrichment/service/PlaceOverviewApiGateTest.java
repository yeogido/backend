package com.yeogido.backend.domain.placeenrichment.service;

import com.yeogido.backend.domain.placeenrichment.client.PlaceOverviewApiScope;
import com.yeogido.backend.domain.placeenrichment.entity.PlaceOverviewApiOperation;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiDeferredException;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailure;
import com.yeogido.backend.domain.placeenrichment.exception.ExternalApiFailureKind;
import com.yeogido.backend.domain.placeenrichment.exception.PlaceEnrichmentErrorCode;
import com.yeogido.backend.domain.placeenrichment.repository.PlaceOverviewApiOperationRepository;
import com.yeogido.backend.global.config.JpaConfig;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers_skip_column_definitions=true"})
@ActiveProfiles("test")
@Import(JpaConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlaceOverviewApiGateTest {
  @Autowired PlaceOverviewApiOperationRepository operations;
  @Autowired PlatformTransactionManager transactionManager;
  private MutableClock clock;
  private PlaceOverviewApiGate gate;

  @BeforeEach
  void setUp() {
    operations.deleteAll();
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/searchKeyword2"));
    operations.saveAndFlush(new PlaceOverviewApiOperation("tour", "/detailCommon2"));
    operations.saveAndFlush(new PlaceOverviewApiOperation("daum", "searchWeb"));
    clock = new MutableClock();
    gate = newGate();
  }

  @ParameterizedTest
  @CsvSource({
      "0, 60, 30", "604801, 60, 30",
      "86400, 0, 30", "86400, 86401, 30",
      "86400, 60, 0", "86400, 60, 86401"
  })
  void invalidCooldownUsesExistingEnrichmentConfigurationError(
      long dailySeconds, long rateSeconds, long transientSeconds) {
    assertThatThrownBy(() -> new PlaceOverviewApiGate(operations, transactionManager,
        dailySeconds, rateSeconds, transientSeconds, clock))
        .isInstanceOfSatisfying(GeneralException.class,
            failure -> assertThat(failure.getErrorCode())
                .isEqualTo(PlaceEnrichmentErrorCode.INVALID_ENRICHMENT_CONFIG));
  }

  @Test
  void dailyQuotaIsSharedAcrossInstancesAndBlocksOnlyItsOperationUntilDue() {
    gate.recordFailure(failure(ExternalApiFailureKind.DAILY_QUOTA, null));

    assertThatThrownBy(() -> newGate().check("tour", "/searchKeyword2"))
        .isInstanceOfSatisfying(ExternalApiDeferredException.class,
            deferred -> assertThat(deferred.getRetryAt()).isEqualTo(clock.now().plusDays(1)));
    assertThatCode(() -> gate.check("tour", "/detailCommon2")).doesNotThrowAnyException();
    assertThatCode(() -> gate.check("daum", "searchWeb")).doesNotThrowAnyException();

    clock.advance(Duration.ofDays(1));
    assertThatCode(() -> newGate().check("tour", "/searchKeyword2")).doesNotThrowAnyException();
  }

  @Test
  void dailyQuotaHonorsLongerRetryAfterWithoutShorteningConfiguredCooldown() {
    gate.recordFailure(failure(ExternalApiFailureKind.DAILY_QUOTA, Duration.ofDays(2)));
    assertThatThrownBy(() -> gate.check("tour", "/searchKeyword2"))
        .isInstanceOfSatisfying(ExternalApiDeferredException.class,
            deferred -> assertThat(deferred.getRetryAt()).isEqualTo(clock.now().plusDays(2)));
    gate.release("tour", "/searchKeyword2");
    gate.recordFailure(failure(ExternalApiFailureKind.DAILY_QUOTA, Duration.ofMinutes(5)));
    assertThatThrownBy(() -> gate.check("tour", "/searchKeyword2"))
        .isInstanceOfSatisfying(ExternalApiDeferredException.class,
            deferred -> assertThat(deferred.getRetryAt()).isEqualTo(clock.now().plusDays(1)));
  }

  @Test
  void retryAfterIsHonoredAndShorterFailuresCannotShortenSharedCooldown() {
    gate.recordFailure(failure(ExternalApiFailureKind.SERVER, Duration.ofMinutes(3)));
    gate.recordFailure(failure(ExternalApiFailureKind.RATE_LIMIT, null));

    assertThatThrownBy(() -> gate.check("tour", "/searchKeyword2"))
        .isInstanceOfSatisfying(ExternalApiDeferredException.class,
            deferred -> assertThat(deferred.getRetryAt()).isEqualTo(clock.now().plusMinutes(3)));
    clock.advance(Duration.ofMinutes(3));
    assertThatCode(() -> gate.check("tour", "/searchKeyword2")).doesNotThrowAnyException();
  }

  @Test
  void credentialFailureNeedsExplicitReleaseAndInvalidResponsesDoNotBlockOtherPlaces() {
    gate.recordFailure(failure(ExternalApiFailureKind.INVALID_RESPONSE, null));
    gate.recordFailure(failure(ExternalApiFailureKind.REQUEST, null));
    assertThatCode(() -> gate.check("tour", "/searchKeyword2")).doesNotThrowAnyException();
    gate.recordFailure(failure(ExternalApiFailureKind.AUTHENTICATION, null));
    assertThatThrownBy(() -> gate.check("tour", "/searchKeyword2"))
        .isInstanceOfSatisfying(ExternalApiDeferredException.class,
            deferred -> assertThat(deferred.getRetryAt()).isEqualTo(PlaceOverviewApiGate.MANUAL_RELEASE_REQUIRED));

    gate.release("tour", "/searchKeyword2");
    assertThatCode(() -> gate.check("tour", "/searchKeyword2")).doesNotThrowAnyException();
  }

  @Test
  void unknownOperationFailsClosedInsteadOfCreatingUnlockedState() {
    assertThatThrownBy(() -> gate.check("tour", "unknown"))
        .isInstanceOf(ExternalApiDeferredException.class);
    assertThat(operations.findById("tour:unknown")).isEmpty();
  }

  @Test
  void scopeDoesNotAffectOtherClientUsersAndRestoresNestedScopes() {
    var count = new AtomicInteger();
    gate.recordFailure(failure(ExternalApiFailureKind.DAILY_QUOTA, null));
    assertThat(PlaceOverviewApiScope.call("tour", "/searchKeyword2", count::incrementAndGet)).isEqualTo(1);
    try (var scope = PlaceOverviewApiScope.open(gate)) {
      assertThatThrownBy(() -> PlaceOverviewApiScope.call("tour", "/searchKeyword2", count::incrementAndGet))
          .isInstanceOf(ExternalApiDeferredException.class);
      try (var nested = PlaceOverviewApiScope.open(newGate())) {
        assertThat(PlaceOverviewApiScope.call("tour", "/detailCommon2", count::incrementAndGet)).isEqualTo(2);
      }
      assertThatThrownBy(() -> PlaceOverviewApiScope.call("tour", "/searchKeyword2", count::incrementAndGet))
          .isInstanceOf(ExternalApiDeferredException.class);
    }
    assertThat(PlaceOverviewApiScope.call("tour", "/searchKeyword2", count::incrementAndGet)).isEqualTo(3);
  }

  @Test
  void scopeRecordsFailureAndDefersSubsequentCallsWithoutInvokingSupplier() {
    var failure = failure(ExternalApiFailureKind.TIMEOUT, null);
    var count = new AtomicInteger();
    try (var scope = PlaceOverviewApiScope.open(gate)) {
      assertThatThrownBy(() -> PlaceOverviewApiScope.call("tour", "/searchKeyword2", () -> {
        count.incrementAndGet();
        throw failure;
      })).isSameAs(failure);
      assertThatThrownBy(() -> PlaceOverviewApiScope.call("tour", "/searchKeyword2", count::incrementAndGet))
          .isInstanceOf(ExternalApiDeferredException.class);
    }
    assertThat(count.get()).isEqualTo(1);
  }

  private PlaceOverviewApiGate newGate() {
    return new PlaceOverviewApiGate(operations, transactionManager, 86400, 60, 30, clock);
  }

  private ExternalApiFailure failure(ExternalApiFailureKind kind, Duration retryAfter) {
    return new ExternalApiFailure(PlaceEnrichmentErrorCode.DAUM_SEARCH_API_ERROR, "tour",
        "/searchKeyword2", kind, null, null, retryAfter);
  }

  private static class MutableClock extends Clock {
    private Instant instant = Instant.parse("2026-11-08T00:00:00Z");

    void advance(Duration duration) { instant = instant.plus(duration); }
    LocalDateTime now() { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
  }
}
