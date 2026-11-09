package com.yeogido.backend.domain.placeenrichment.exception;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.Getter;

@Getter
public class ExternalApiDeferredException extends RuntimeException {
  private final String provider;
  private final String operation;
  private final LocalDateTime retryAt;

  public ExternalApiDeferredException(String provider, String operation, LocalDateTime retryAt) {
    this.provider = provider;
    this.operation = operation;
    this.retryAt = Objects.requireNonNull(retryAt);
  }
}
