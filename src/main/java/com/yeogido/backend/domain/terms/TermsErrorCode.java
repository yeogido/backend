package com.yeogido.backend.domain.terms;

import com.yeogido.backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermsErrorCode implements ErrorCode {
  REQUIRED_AGREEMENT(HttpStatus.BAD_REQUEST, "TERMS4001", "필수 약관에 모두 동의해야 합니다."),
  VERSION_MISMATCH(HttpStatus.CONFLICT, "TERMS4091", "약관이 변경되었습니다. 최신 약관을 확인해 주세요."),
  NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "TERMS5031", "현재 이용 가능한 필수 약관이 준비되지 않았습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
