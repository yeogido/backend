package com.yeogido.backend.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yeogido.backend.domain.auth.exception.AuthErrorCode;
import com.yeogido.backend.global.exception.GeneralException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

  private static final String EMAIL = "abc@example.com";
  private static final String CODE_KEY = "auth:email-verification-code:abc@example.com";
  private static final String TOKEN_PREFIX = "auth:email-verification-token:";

  @Mock
  private StringRedisTemplate stringRedisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private EmailVerificationService emailVerificationService;

  @BeforeEach
  void setUp() {
    emailVerificationService = new EmailVerificationService(stringRedisTemplate);
    ReflectionTestUtils.setField(emailVerificationService, "codeExpirationMinutes", 5L);
    ReflectionTestUtils.setField(emailVerificationService, "tokenExpirationMinutes", 10L);
    when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  @DisplayName("회원가입 이메일 인증번호를 6자리 숫자로 생성하고 Redis에 저장한다")
  void issueCode() {
    String authCode = emailVerificationService.issueCode(EMAIL);

    assertThat(authCode).matches("\\d{6}");
    verify(valueOperations).set(CODE_KEY, authCode, Duration.ofMinutes(5));
  }

  @Test
  @DisplayName("인증번호가 일치하면 기존 인증번호를 삭제하고 이메일 인증 토큰을 발급한다")
  void verifyCodeAndIssueTokenSuccess() {
    when(valueOperations.get(CODE_KEY)).thenReturn("123456");

    String token = emailVerificationService.verifyCodeAndIssueToken(EMAIL, "123456");

    assertThat(token).isNotBlank();
    verify(stringRedisTemplate).delete(CODE_KEY);
    verify(valueOperations).set(TOKEN_PREFIX + token, EMAIL, Duration.ofMinutes(10));
  }

  @Test
  @DisplayName("인증번호가 일치하지 않으면 이메일 인증번호 예외를 반환한다")
  void verifyCodeAndIssueTokenFail() {
    when(valueOperations.get(CODE_KEY)).thenReturn("123456");

    assertThatThrownBy(() -> emailVerificationService.verifyCodeAndIssueToken(EMAIL, "000000"))
      .isInstanceOfSatisfying(GeneralException.class, exception ->
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_EMAIL_VERIFICATION_CODE)
      );

    verify(stringRedisTemplate, never()).delete(CODE_KEY);
  }

  @Test
  @DisplayName("이메일 인증 토큰의 저장 이메일이 요청 이메일과 일치하면 검증에 성공한다")
  void verifyTokenSuccess() {
    String token = "email-verification-token";
    when(valueOperations.get(TOKEN_PREFIX + token)).thenReturn(EMAIL);

    emailVerificationService.verifyToken(EMAIL, token);

    verify(valueOperations).get(TOKEN_PREFIX + token);
  }

  @Test
  @DisplayName("이메일 인증 토큰이 없거나 이메일이 다르면 이메일 인증 토큰 예외를 반환한다")
  void verifyTokenFail() {
    String token = "email-verification-token";
    when(valueOperations.get(TOKEN_PREFIX + token)).thenReturn("other@example.com");

    assertThatThrownBy(() -> emailVerificationService.verifyToken(EMAIL, token))
      .isInstanceOfSatisfying(GeneralException.class, exception ->
        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_EMAIL_VERIFICATION_TOKEN)
      );
  }
}
