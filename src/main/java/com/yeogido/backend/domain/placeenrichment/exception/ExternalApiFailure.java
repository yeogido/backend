package com.yeogido.backend.domain.placeenrichment.exception;

import com.yeogido.backend.global.exception.ErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import com.yeogido.backend.global.logging.SafeLogDetails;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;

/** 응답 본문·인증정보·원본 예외를 제외한 외부 오류 분류 정보 */
@Getter
public class ExternalApiFailure extends GeneralException {
  public static final LocalDateTime MAX_RETRY_AT = LocalDateTime.of(9999, 1, 1, 0, 0);

  private final String provider;
  private final String operation;
  private final ExternalApiFailureKind kind;
  private final Integer upstreamHttpStatus;
  private final String providerCode;
  private final Duration retryAfter;

  public ExternalApiFailure(ErrorCode errorCode, String provider, String operation,
      ExternalApiFailureKind kind, Integer upstreamHttpStatus, String providerCode, Duration retryAfter) {
    super(errorCode);
    this.provider = provider;
    this.operation = operation;
    this.kind = kind;
    this.upstreamHttpStatus = upstreamHttpStatus;
    this.providerCode = providerCode != null && providerCode.matches("-?[0-9]{1,4}") ? providerCode : null;
    this.retryAfter = retryAfter == null || retryAfter.isNegative() ? null : retryAfter;
  }

  public static ExternalApiFailure http(ErrorCode errorCode, String provider, String operation,
      RestClientResponseException exception) {
    int status = exception.getStatusCode().value();
    ExternalApiFailureKind kind;
    if (status == 401 || status == 403) kind = ExternalApiFailureKind.AUTHENTICATION;
    else if (status == 408) kind = ExternalApiFailureKind.TIMEOUT;
    else if (status == 429) kind = ExternalApiFailureKind.RATE_LIMIT;
    else if (status >= 500 && status < 600) kind = ExternalApiFailureKind.SERVER;
    else kind = ExternalApiFailureKind.REQUEST;
    String header = exception.getResponseHeaders() == null ? null
        : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
    return new ExternalApiFailure(errorCode, provider, operation, kind, status, null, parseRetryAfter(header));
  }

  public String failureType() {
    return switch (kind) {
      case DAILY_QUOTA -> "daily_quota_exhausted";
      case RATE_LIMIT -> "quota_or_rate_limit";
      case TIMEOUT -> "timeout";
      case CONNECTION -> "connection_error";
      case SERVER -> "provider_server_error";
      case AUTHENTICATION -> upstreamHttpStatus != null && upstreamHttpStatus == 403
          ? "permission_or_access_failure" : "authentication_failure";
      case CONFIGURATION -> "configuration_error";
      case REQUEST -> "request_error";
      case INVALID_RESPONSE -> "invalid_response";
    };
  }

  public static ExternalApiFailure client(ErrorCode errorCode, String provider, String operation,
      RuntimeException exception) {
    ExternalApiFailureKind kind = switch (SafeLogDetails.failureType(exception)) {
      case "timeout" -> ExternalApiFailureKind.TIMEOUT;
      case "connection_error" -> ExternalApiFailureKind.CONNECTION;
      default -> ExternalApiFailureKind.INVALID_RESPONSE;
    };
    return new ExternalApiFailure(errorCode, provider, operation, kind, null, null, null);
  }

  // 초 단위 대기 시간과 HTTP 날짜 형식의 Retry-After 해석 및 잘못된 값 제외
  public static Duration parseRetryAfter(String header) {
    if (header == null || header.length() > 128) return null;
    try {
      Duration delay = header.trim().matches("[0-9]+")
          ? Duration.ofSeconds(Long.parseLong(header.trim()))
          : Duration.between(Instant.now(), ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
      if (delay.isNegative()) return Duration.ZERO;
      return delay;
    } catch (DateTimeParseException | ArithmeticException | NumberFormatException ignored) {
      return null;
    }
  }

  // 긴 외부 대기 시간으로 인한 시각 범위 초과 방지를 위한 DB 저장 가능 상한 적용
  public static LocalDateTime retryAt(LocalDateTime now, Duration delay) {
    try {
      LocalDateTime result = now.plus(delay);
      return result.isAfter(MAX_RETRY_AT) ? MAX_RETRY_AT : result;
    } catch (DateTimeException | ArithmeticException exception) {
      return MAX_RETRY_AT;
    }
  }

}
