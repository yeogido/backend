package com.yeogido.backend.domain.placeenrichment.exception;

public enum ExternalApiFailureKind {
  DAILY_QUOTA,
  RATE_LIMIT,
  TIMEOUT,
  CONNECTION,
  SERVER,
  AUTHENTICATION,
  CONFIGURATION,
  REQUEST,
  INVALID_RESPONSE
}
