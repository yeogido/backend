package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.dto.EmailCheckRequest;
import com.yeogido.backend.domain.auth.dto.EmailCheckResponse;
import com.yeogido.backend.domain.auth.dto.LoginRequest;
import com.yeogido.backend.domain.auth.dto.LogoutRequest;
import com.yeogido.backend.domain.auth.dto.PasswordResetCodeRequest;
import com.yeogido.backend.domain.auth.dto.PasswordVerifyCodeRequest;
import com.yeogido.backend.domain.auth.dto.PasswordVerifyCodeResponse;
import com.yeogido.backend.domain.auth.dto.SignUpRequest;
import com.yeogido.backend.domain.auth.dto.SignUpResponse;
import com.yeogido.backend.domain.auth.dto.SocialLoginRequest;
import com.yeogido.backend.domain.auth.dto.TokenResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  public SignUpResponse signUp(SignUpRequest request) {
    return new SignUpResponse(1L, request.email(), request.nickname());
  }

  public TokenResponse login(LoginRequest request) {
    return new TokenResponse(
      "mock_access_token_for_" + request.email(),
      "mock_refresh_token_for_" + request.email(),
      1L,
      "테스트유저"
    );
  }

  public TokenResponse socialLogin(SocialLoginRequest request) {
    return new TokenResponse(
      "mock_social_access_token",
      "mock_social_refresh_token",
      2L,
      "소셜유저"
    );
  }

  public EmailCheckResponse checkEmail(EmailCheckRequest request) {
    return new EmailCheckResponse(true);
  }

  public void logout(LogoutRequest request) {
  }

  public void sendResetCode(PasswordResetCodeRequest request) {
  }

  public PasswordVerifyCodeResponse verifyResetCode(PasswordVerifyCodeRequest request) {
    return new PasswordVerifyCodeResponse("mock_verification_token_12345");
  }
}
