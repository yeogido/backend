package com.yeogido.backend.domain.placeenrichment.exception;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class ExternalApiDeferredException extends RuntimeException {
  private final String provider;
  private final String operation;
  // 재시도 시각의 null 값으로 수동 차단 해제 대기 표현
  private final LocalDateTime retryAt;

  public ExternalApiDeferredException(String provider, String operation, LocalDateTime retryAt) {
    this.provider = provider;
    this.operation = operation;
    this.retryAt = retryAt;
  }
}
