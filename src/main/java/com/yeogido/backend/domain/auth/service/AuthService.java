package com.yeogido.backend.domain.auth.service;

import com.yeogido.backend.domain.auth.dto.EmailCheckResDTO;
import com.yeogido.backend.domain.auth.dto.LoginReqDTO;
import com.yeogido.backend.domain.auth.dto.PasswordSendCodeReqDTO;
import com.yeogido.backend.domain.auth.dto.PasswordVerifyCodeReqDTO;
import com.yeogido.backend.domain.auth.dto.PasswordVerifyCodeResDTO;
import com.yeogido.backend.domain.auth.dto.SignUpReqDTO;
import com.yeogido.backend.domain.auth.dto.SignUpResDTO;
import com.yeogido.backend.domain.auth.dto.SocialLoginReqDTO;
import com.yeogido.backend.domain.auth.dto.TokenResDTO;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  public SignUpResDTO signUp(SignUpReqDTO request) {
    return new SignUpResDTO(1L);
  }

  public TokenResDTO login(LoginReqDTO request) {
    return new TokenResDTO(
      1L,
      "mock_access_token_for_" + request.email(),
      "mock_refresh_token_for_" + request.email()
    );
  }

  public TokenResDTO socialLogin(SocialLoginReqDTO request) {
    return new TokenResDTO(
      2L,
      "mock_social_access_token",
      "mock_social_refresh_token"
    );
  }

  public EmailCheckResDTO checkEmail(String email) {
    return new EmailCheckResDTO(true);
  }

  public void logout(String authorization) {
  }

  public void sendResetCode(PasswordSendCodeReqDTO request) {
  }

  public PasswordVerifyCodeResDTO verifyResetCode(PasswordVerifyCodeReqDTO request) {
    return new PasswordVerifyCodeResDTO("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  }
}
